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
# Lap detection (reusing logic from lap_detection_test_again.ipynb)
# ---------------------------------------------------------------------------


@dataclass
class LapConfig:
    accel_threshold: float = 12.0
    gap_fill_seconds: float = 7.0
    bout_filter_seconds: float = 30.0


def add_is_swimming(df: pd.DataFrame, cfg: LapConfig) -> None:
    """Create raw `is_swimming` flag from accel_combined threshold (in-place)."""
    if "accel_combined" not in df.columns:
        raise ValueError("DataFrame must have 'accel_combined' column")

    df["is_swimming"] = (df["accel_combined"] > cfg.accel_threshold).astype(int)


def clean_is_swimming(df: pd.DataFrame, cfg: LapConfig) -> None:
    """Apply gap filling and bout filtering as in lap_detection_test_again.

    - Gap filling: binary_closing with window ~gap_fill_seconds
    - Bout filtering: binary_opening with window ~bout_filter_seconds
    """
    if "is_swimming" not in df.columns:
        raise ValueError("DataFrame must have 'is_swimming' column")

    dt = estimate_sampling_interval_seconds(df)
    gap_fill_samples = max(1, int(cfg.gap_fill_seconds / dt))
    bout_filter_samples = max(1, int(cfg.bout_filter_seconds / dt))

    is_swimming_bool = df["is_swimming"].astype(bool).to_numpy()

    structure_gap = np.ones(gap_fill_samples, dtype=bool)
    gap_filled = binary_closing(is_swimming_bool, structure=structure_gap)

    structure_bout = np.ones(bout_filter_samples, dtype=bool)
    cleaned = binary_opening(gap_filled, structure=structure_bout)

    df["is_swimming_cleaned"] = cleaned.astype(int)


@dataclass
class LapInfo:
    lap_number: int
    start_time: pd.Timestamp
    end_time: pd.Timestamp
    lap_time: float  # seconds


def detect_laps_from_is_swimming(df: pd.DataFrame) -> List[LapInfo]:
    """Detect lap-like bouts from `is_swimming_cleaned`.

    This reuses the idea from lap_detection_test_again: after cleaning,
    continuous sequences of 1s represent sustained swimming. Here we treat
    each continuous sequence of 1s as a "lap" segment.
    """
    if "is_swimming_cleaned" not in df.columns:
        raise ValueError("DataFrame must have 'is_swimming_cleaned' column")
    if "datetime" not in df.columns:
        raise ValueError("DataFrame must have 'datetime' column")

    cleaned = df["is_swimming_cleaned"].to_numpy()
    times = df["datetime"].to_numpy()

    laps: List[LapInfo] = []
    in_bout = False
    start_idx: Optional[int] = None
    lap_number = 0

    for i, val in enumerate(cleaned):
        if not in_bout and val == 1:
            # Start of a new swimming bout
            in_bout = True
            start_idx = i
        elif in_bout and val == 0:
            # End of the bout
            end_idx = i - 1
            if start_idx is not None and end_idx > start_idx:
                lap_number += 1
                start_time = times[start_idx]
                end_time = times[end_idx]
                lap_time = (end_time - start_time).total_seconds()
                laps.append(LapInfo(lap_number, start_time, end_time, lap_time))
            in_bout = False
            start_idx = None

    # Handle case where signal ends in a bout
    if in_bout and start_idx is not None and start_idx < len(cleaned) - 1:
        lap_number += 1
        start_time = times[start_idx]
        end_time = times[-1]
        lap_time = (end_time - start_time).total_seconds()
        laps.append(LapInfo(lap_number, start_time, end_time, lap_time))

    return laps


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

    # Swimming detection / lap detection
    add_is_swimming(df, lap_config)
    clean_is_swimming(df, lap_config)
    laps = detect_laps_from_is_swimming(df)

    # Stroke metrics per lap
    lap_metrics = compute_lap_metrics(df, laps, stroke_type_col=stroke_type_col)

    per_lap_results = lap_metrics_to_dicts(lap_metrics)
    session_averages = compute_session_averages(lap_metrics)

    return per_lap_results, session_averages


def run_pipeline_from_db(
    db_path: str,
    table_name: str = "sensor_data",
    stroke_type_col: str = "stroke_type",
    lap_config: LapConfig = LapConfig(),
) -> Tuple[List[Dict], Dict[str, float]]:
    """Convenience wrapper: load from DB and run the full pipeline."""
    df = load_from_db(db_path, table_name=table_name)
    return run_pipeline_from_df(df, lap_config=lap_config, stroke_type_col=stroke_type_col)


def run_pipeline_from_csv(
    csv_path: str,
    stroke_type_col: str = "stroke_type",
    lap_config: LapConfig = LapConfig(),
) -> Tuple[List[Dict], Dict[str, float]]:
    """Convenience wrapper: load from CSV and run the full pipeline."""
    df = load_from_csv(csv_path)
    return run_pipeline_from_df(df, lap_config=lap_config, stroke_type_col=stroke_type_col)
