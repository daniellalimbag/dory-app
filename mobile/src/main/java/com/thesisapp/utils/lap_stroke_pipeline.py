"""Integrated lap and stroke metrics pipeline.

This module reuses the existing logic from:
- lap_detection_test_again.ipynb (lap / swimming-bout detection)
- stroke_metric_test.ipynb (stroke detection using accel_y + accel_z)

It provides functions to:
- Load IMU data from SQLite DB (sensor_data) or CSV
- Build combined accel/gyro signals
- Detect swimming bouts and derive lap boundaries
- Run stroke detection per lap using the existing accel-based pipeline
- Compute per-lap kinematic metrics and overall session averages

The core stroke and lap logic follows the notebooks; this module mostly wraps
that logic into reusable functions.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Dict, Optional, Tuple

import sqlite3
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.signal import butter, filtfilt, find_peaks
from scipy.ndimage import binary_closing, binary_opening


# ---------------------------------------------------------------------------
# Loading utilities (reused style from stroke_metric_test & lap_detection)
# ---------------------------------------------------------------------------


def load_from_db(
    db_path: str,
    table_name: str = "sensor_data",
    tz: str = "Asia/Manila",
) -> pd.DataFrame:
    """Load sensor data from a SQLite DB and create a `datetime` column.

    Expects a millisecond `unix_ts` or `timestamp` column.
    """
    path = Path(db_path)
    if not path.exists():
        raise FileNotFoundError(f"DB path not found: {db_path}")

    conn = sqlite3.connect(str(path))
    try:
        df = pd.read_sql(f"SELECT * FROM {table_name};", conn)
    finally:
        conn.close()

    if "unix_ts" in df.columns:
        dt = pd.to_datetime(df["unix_ts"], unit="ms", utc=True)
    elif "timestamp" in df.columns:
        dt = pd.to_datetime(df["timestamp"], unit="ms", utc=True)
    else:
        raise ValueError("DB table must have 'unix_ts' or 'timestamp' column")

    df["datetime"] = dt.dt.tz_convert(tz)
    return df


def load_from_csv(
    csv_path: str,
    tz: str = "Asia/Manila",
) -> pd.DataFrame:
    """Load sensor data from CSV and create a `datetime` column.

    Expects a millisecond `unix_ts` or `timestamp` column.
    """
    path = Path(csv_path)
    if not path.exists():
        raise FileNotFoundError(f"CSV path not found: {csv_path}")

    df = pd.read_csv(path)

    if "unix_ts" in df.columns:
        dt = pd.to_datetime(df["unix_ts"], unit="ms", utc=True)
    elif "timestamp" in df.columns:
        dt = pd.to_datetime(df["timestamp"], unit="ms", utc=True)
    else:
        raise ValueError("CSV must have 'unix_ts' or 'timestamp' column")

    df["datetime"] = dt.dt.tz_convert(tz)
    return df


# ---------------------------------------------------------------------------
# Combined signals and sampling utilities
# ---------------------------------------------------------------------------


def add_accel_combined(df: pd.DataFrame) -> None:
    """Add `accel_combined` = |ax| + |ay| + |az| (in-place)."""
    for col in ("accel_x", "accel_y", "accel_z"):
        if col not in df.columns:
            raise ValueError(f"Missing column '{col}' for accel_combined")

    df["accel_combined"] = (
        df["accel_x"].abs()
        + df["accel_y"].abs()
        + df["accel_z"].abs()
    )


def add_gyro_combined(df: pd.DataFrame) -> None:
    """Add `gyro_combined` = sqrt(gx^2 + gy^2 + gz^2) (in-place)."""
    for col in ("gyro_x", "gyro_y", "gyro_z"):
        if col not in df.columns:
            raise ValueError(f"Missing column '{col}' for gyro_combined")

    df["gyro_combined"] = np.sqrt(
        df["gyro_x"] ** 2 + df["gyro_y"] ** 2 + df["gyro_z"] ** 2
    )

def detect_lap_turns(segment_df: pd.DataFrame, threshold: float, debounce_seconds: int = 35) -> List[pd.Timestamp]:
    """Identifies falling edges in gyro data to mark lap turns, with debouncing."""
    lap_turn_times = []
    last_detected_lap_turn_time = None # Initialize for debouncing

    # Ensure the dataframe is sorted by datetime if not already
    segment_df_sorted = segment_df.sort_values(by='datetime').reset_index(drop=True)

    for i in range(1, len(segment_df_sorted)):
        current_gyro = segment_df_sorted.loc[i, 'gyro_combined_filtered']
        previous_gyro = segment_df_sorted.loc[i-1, 'gyro_combined_filtered']
        current_datetime = segment_df_sorted.loc[i, 'datetime']

        # Condition for detecting a 'falling edge' where the signal crosses below the threshold
        if current_gyro < threshold and previous_gyro >= threshold:
            # Apply debouncing mechanism
            if last_detected_lap_turn_time is None or (current_datetime - last_detected_lap_turn_time >= pd.Timedelta(seconds=debounce_seconds)):
                lap_turn_times.append(current_datetime)
                last_detected_lap_turn_time = current_datetime

    return lap_turn_times

def estimate_sampling_interval_seconds(df: pd.DataFrame) -> float:
    """Estimate average sampling interval (seconds/sample) from `datetime`."""
    if "datetime" not in df.columns:
        raise ValueError("DataFrame must have 'datetime' column")

    diffs = df["datetime"].diff().dropna()
    if diffs.empty:
        raise ValueError("Not enough data to estimate sampling interval")

    return diffs.dt.total_seconds().mean()


def estimate_sampling_rate(df: pd.DataFrame) -> float:
    """Estimate sampling rate (Hz) from `datetime`.

    Matches the helper used in stroke_metric_test.ipynb.
    """
    interval = estimate_sampling_interval_seconds(df)
    return 1.0 / interval


# ---------------------------------------------------------------------------
# Bout detection (reusing logic from lap_detection_test_again.ipynb)
# ---------------------------------------------------------------------------


@dataclass
class BoutConfig:
    accel_threshold: float = 12.0
    gap_fill_seconds: float = 7.0
    bout_filter_seconds: float = 30.0


def add_is_swimming(df: pd.DataFrame, cfg: BoutConfig) -> None:
    """Create raw `is_swimming` flag from accel_combined threshold (in-place)."""
    if "accel_combined" not in df.columns:
        raise ValueError("DataFrame must have 'accel_combined' column")

    df["is_swimming"] = (df["accel_combined"] > cfg.accel_threshold).astype(int)


def clean_is_swimming(df: pd.DataFrame, cfg: BoutConfig) -> None:
    """Apply gap filling and bout filtering as in lap_detection_test_again.

    - Gap filling: binary_closing with window ~gap_fill_seconds
    - Bout filtering: binary_opening with window ~bout_filter_seconds
    """
    if "is_swimming" not in df.columns:
        raise ValueError("DataFrame must have 'is_swimming' column")

    dt = estimate_sampling_interval_seconds(df)
    gap_fill_samples = int(cfg.gap_fill_seconds / dt)
    bout_filter_samples = int(cfg.bout_filter_seconds / dt)

    is_swimming_bool = df["is_swimming"].astype(bool).to_numpy()

    structure_gap = np.ones(gap_fill_samples, dtype=bool)
    gap_filled = binary_closing(is_swimming_bool, structure=structure_gap)

    structure_bout = np.ones(bout_filter_samples, dtype=bool)
    cleaned = binary_opening(gap_filled, structure=structure_bout)

    df["is_swimming_cleaned"] = cleaned.astype(int)

# ---------------------------------------------------------------------------
# Bout detection (reusing logic from lap_detection_test_again.ipynb)
# ---------------------------------------------------------------------------


@dataclass
class LapConfig:
    window_size_seconds: int = 1
    cutoff_frequency_hz: float = 3.0
    lap_turn_threshold: float = 1.2
    boundary_buffer_seconds: int = 35
    debounce_seconds: int = 35

def add_gyro_combined_smoothed(df: pd.DataFrame, cfg: LapConfig) -> None:
    if "gyro_combined" not in df.columns:
        raise ValueError("DataFrame must have 'gyro_combined' column")

    sampling_interval_seconds = estimate_sampling_interval_seconds(df)
    window_size_samples = int(cfg.window_size_seconds / sampling_interval_seconds)

    # Ensure window size is at least 1
    if window_size_samples < 1:
        window_size_samples = 1

    df['gyro_combined_smoothed'] = df['gyro_combined'].rolling(window=window_size_samples, center=True).mean()

def add_gyro_combined_filtered(df: pd.DataFrame, cfg: LapConfig) -> None:
    signal_cleaned = df['gyro_combined_smoothed'].bfill().ffill()

    sampling_frequency_hz = estimate_sampling_rate(df)
    cutoff_frequency_hz = cfg.cutoff_frequency_hz

    dt = 1 / sampling_frequency_hz
    RC = 1 / (2 * np.pi * cutoff_frequency_hz)
    alpha = dt / (RC + dt)

    filtered_signal = np.zeros_like(signal_cleaned, dtype=float)

    # Initialize the first element of the filtered signal with the first clean signal value
    if len(signal_cleaned) > 0:
        filtered_signal[0] = signal_cleaned.iloc[0] if isinstance(signal_cleaned, pd.Series) else signal_cleaned[0]

    # Apply the filter equation
    for i in range(1, len(signal_cleaned)):
        current_value = signal_cleaned.iloc[i] if isinstance(signal_cleaned, pd.Series) else signal_cleaned[i]
        previous_filtered_value = filtered_signal[i-1]
        filtered_signal[i] = alpha * current_value + (1 - alpha) * previous_filtered_value

    df['gyro_combined_filtered'] = filtered_signal


@dataclass
class LapInfo:
    lap_number: int
    start_time: pd.Timestamp
    end_time: pd.Timestamp
    lap_time: float  # seconds


def detect_laps(df: pd.DataFrame, cfg: LapConfig) -> List[LapInfo]:
    all_laps: List[LapInfo] = []
    current_lap_number = 1

    # 1. Identify swimming blocks safely (without modifying the original dataframe)
    temp_df = df.copy()
    temp_df['block_id'] = (temp_df['is_swimming_cleaned'] != temp_df['is_swimming_cleaned'].shift(1)).cumsum()
    swimming_blocks = temp_df[temp_df['is_swimming_cleaned'] == 1]

    # 2. Get the start and end times for each swimming bout
    swimming_ranges = []
    if not swimming_blocks.empty:
        for _, group in swimming_blocks.groupby('block_id'):
            swimming_ranges.append((group['datetime'].min(), group['datetime'].max()))

    for start_time_bout, end_time_bout in swimming_ranges:

        bout_segment_df = temp_df[
            (temp_df['datetime'] >= start_time_bout) &
            (temp_df['datetime'] <= end_time_bout)
        ]

        if bout_segment_df.empty:
            continue

        # 4. Detect turns
        detected_lap_turns = detect_lap_turns(bout_segment_df, cfg.lap_turn_threshold, cfg.debounce_seconds)

        # 5. Filter turns near the boundaries
        filtered_start = start_time_bout + pd.Timedelta(seconds=cfg.boundary_buffer_seconds)
        filtered_end = end_time_bout - pd.Timedelta(seconds=cfg.boundary_buffer_seconds)

        filtered_lap_turns = [
            t for t in detected_lap_turns
            if filtered_start <= t <= filtered_end
        ]

        # 6. Convert turns into LapInfo objects
        # A bout starts at `start_time_bout`, has N turns, and ends at `end_time_bout`
        current_lap_start = start_time_bout

        for turn_time in filtered_lap_turns:
            lap_time_seconds = (turn_time - current_lap_start).total_seconds()

            all_laps.append(LapInfo(
                lap_number=current_lap_number,
                start_time=current_lap_start,
                end_time=turn_time,
                lap_time=lap_time_seconds
            ))
            current_lap_number += 1
            current_lap_start = turn_time

        # Add the final lap (from the last turn to the end of the bout)
        final_lap_time_seconds = (end_time_bout - current_lap_start).total_seconds()

        # Prevent appending 0-second laps if the start perfectly matches the end
        if final_lap_time_seconds > 0:
            all_laps.append(LapInfo(
                lap_number=current_lap_number,
                start_time=current_lap_start,
                end_time=end_time_bout,
                lap_time=final_lap_time_seconds
            ))
            current_lap_number += 1

    return all_laps

# ---------------------------------------------------------------------------
# Stroke detection (reusing stroke_metric_test.ipynb logic)
# ---------------------------------------------------------------------------


def butter_bandpass_filter(
    data: np.ndarray,
    lowcut: float = 0.25,
    highcut: float = 0.5,
    fs: float = 50.0,
    order: int = 2,
) -> np.ndarray:
    """Band-pass filter used in stroke_metric_test.

    Default lowcut/highcut match the original notebook; the integrated
    pipeline keeps the same logic.
    """
    nyq = 0.5 * fs
    low = lowcut / nyq
    high = highcut / nyq
    b, a = butter(order, [low, high], btype="band")
    return filtfilt(b, a, data)


def identify_stroke_cycles(segment: pd.DataFrame) -> Tuple[int, List[int], np.ndarray]:
    """Stroke cycle identification as in stroke_metric_test.ipynb.

    Uses accel_y and accel_z, band-pass filters, sums into a single
    stroke_signal, and runs `find_peaks`.
    """
    if not all(col in segment.columns for col in ("accel_y", "accel_z")):
        raise ValueError("segment must contain 'accel_y' and 'accel_z' columns")

    fs = estimate_sampling_rate(segment)
    ay_f = butter_bandpass_filter(segment["accel_y"].values, fs=fs)
    az_f = butter_bandpass_filter(segment["accel_z"].values, fs=fs)
    signal = ay_f + az_f

    peaks, _ = find_peaks(signal)
    return len(peaks), list(peaks), signal


# ---------------------------------------------------------------------------
# Per-lap metrics computation
# ---------------------------------------------------------------------------


POOL_LENGTH_METERS = 50.0


@dataclass
class LapMetrics:
    lap_number: int
    start_time: pd.Timestamp
    end_time: pd.Timestamp
    lap_time: float
    stroke_count: int
    stroke_type: Optional[str]
    velocity: float
    stroke_rate_s: float
    stroke_rate_min: float
    stroke_length: float
    stroke_index: float


def _get_stroke_type_for_lap(
    df: pd.DataFrame,
    start_time: pd.Timestamp,
    end_time: pd.Timestamp,
    stroke_type_col: str,
) -> Optional[str]:
    """Return a representative stroke type label within the lap window.

    Uses the most frequent non-null value in the time window if available.
    """
    if stroke_type_col not in df.columns:
        return None

    mask = (df["datetime"] >= start_time) & (df["datetime"] <= end_time)
    subset = df.loc[mask, stroke_type_col].dropna()
    if subset.empty:
        return None

    # Use mode (most frequent) as representative stroke type
    return subset.mode().iloc[0]


def compute_lap_metrics(
    df: pd.DataFrame,
    laps: List[LapInfo],
    stroke_type_col: str = "stroke_type",
) -> List[LapMetrics]:
    """Run stroke detection per lap and compute kinematic metrics.

    - Reuses identify_stroke_cycles for stroke count
    - Uses pool_length = 50 m
    - Computes velocity, stroke rate, stroke length, stroke index
    """
    metrics: List[LapMetrics] = []

    for lap in laps:
        # Extract lap segment
        mask = (df["datetime"] >= lap.start_time) & (df["datetime"] <= lap.end_time)
        segment = df.loc[mask].copy()
        if segment.empty:
            stroke_count = 0
        else:
            stroke_count, _, _ = identify_stroke_cycles(segment)

        lap_time = lap.lap_time

        # Base kinematics
        velocity = POOL_LENGTH_METERS / lap_time if lap_time > 0 else 0.0
        stroke_rate_s = stroke_count / lap_time if lap_time > 0 else 0.0
        stroke_rate_min = stroke_rate_s * 60.0

        if stroke_rate_s > 0:
            stroke_length = velocity / stroke_rate_s
        else:
            stroke_length = 0.0

        stroke_index = velocity * stroke_length

        stroke_type = _get_stroke_type_for_lap(df, lap.start_time, lap.end_time, stroke_type_col)

        metrics.append(
            LapMetrics(
                lap_number=lap.lap_number,
                start_time=lap.start_time,
                end_time=lap.end_time,
                lap_time=lap_time,
                stroke_count=stroke_count,
                stroke_type=stroke_type,
                velocity=velocity,
                stroke_rate_s=stroke_rate_s,
                stroke_rate_min=stroke_rate_min,
                stroke_length=stroke_length,
                stroke_index=stroke_index,
            )
        )

    return metrics


def lap_metrics_to_dicts(lap_metrics: List[LapMetrics]) -> List[Dict]:
    """Convert LapMetrics objects into a list of plain dictionaries."""
    return [
        {
            "lap_number": m.lap_number,
            "start_time": m.start_time,
            "end_time": m.end_time,
            "lap_time": m.lap_time,
            "stroke_count": m.stroke_count,
            "stroke_type": m.stroke_type,
            "velocity": m.velocity,
            "stroke_rate_s": m.stroke_rate_s,
            "stroke_rate_min": m.stroke_rate_min,
            "stroke_length": m.stroke_length,
            "stroke_index": m.stroke_index,
        }
        for m in lap_metrics
    ]


def compute_session_averages(lap_metrics: List[LapMetrics]) -> Dict[str, float]:
    """Compute overall session averages across laps."""
    if not lap_metrics:
        return {
            "avg_lap_time": 0.0,
            "avg_stroke_count": 0.0,
            "avg_velocity": 0.0,
            "avg_stroke_rate": 0.0,
            "avg_stroke_length": 0.0,
            "avg_stroke_index": 0.0,
        }

    df = pd.DataFrame(lap_metrics_to_dicts(lap_metrics))

    return {
        "avg_lap_time": float(df["lap_time"].mean()),
        "avg_stroke_count": float(df["stroke_count"].mean()),
        "avg_velocity": float(df["velocity"].mean()),
        "avg_stroke_rate": float(df["stroke_rate_s"].mean()),
        "avg_stroke_length": float(df["stroke_length"].mean()),
        "avg_stroke_index": float(df["stroke_index"].mean()),
    }


# ---------------------------------------------------------------------------
# Top-level pipeline
# ---------------------------------------------------------------------------


def run_pipeline_from_df(
    df: pd.DataFrame,
    bout_config: BoutConfig = BoutConfig(),
    lap_config: LapConfig = LapConfig(),
    stroke_type_col: str = "stroke_type",
) -> Tuple[List[Dict], Dict[str, float]]:
    """Run full lap + stroke pipeline on an already-loaded DataFrame.

    Steps:
    1) Build accel_combined and gyro_combined
    2) Create and clean is_swimming -> is_swimming_cleaned
    3) Detect laps from is_swimming_cleaned
    4) Run stroke detection per lap (identify_stroke_cycles)
    5) Compute kinematic metrics per lap and overall averages

    Returns:
        (per_lap_results, session_averages)
    """
    # Build combined signals
    add_accel_combined(df)
    add_gyro_combined(df)

    # Swimming bout detection
    add_is_swimming(df, bout_config)
    clean_is_swimming(df, bout_config)

    # Lap detection
    add_gyro_combined_smoothed(df, lap_config)
    add_gyro_combined_filtered(df, lap_config)
    laps = detect_laps(df, lap_config)

    # Stroke metrics per lap
    lap_metrics = compute_lap_metrics(df, laps, stroke_type_col=stroke_type_col)

    per_lap_results = lap_metrics_to_dicts(lap_metrics)
    session_averages = compute_session_averages(lap_metrics)

    return per_lap_results, session_averages


def run_pipeline_from_db(
    db_path: str,
    table_name: str = "sensor_data",
    stroke_type_col: str = "stroke_type",
    bout_config: BoutConfig = BoutConfig(),
    lap_config: LapConfig = LapConfig(),
) -> Tuple[List[Dict], Dict[str, float]]:
    """Convenience wrapper: load from DB and run the full pipeline."""
    df = load_from_db(db_path, table_name=table_name)
    return run_pipeline_from_df(df, bout_config=bout_config, lap_config=lap_config, stroke_type_col=stroke_type_col)


def run_pipeline_from_csv(
    csv_path: str,
    stroke_type_col: str = "stroke_type",
    bout_config: BoutConfig = BoutConfig(),
    lap_config: LapConfig = LapConfig(),
) -> Tuple[List[Dict], Dict[str, float]]:
    """Convenience wrapper: load from CSV and run the full pipeline."""
    df = load_from_csv(csv_path)
    return run_pipeline_from_df(df, bout_config=bout_config, lap_config=lap_config, stroke_type_col=stroke_type_col)
