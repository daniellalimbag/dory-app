from __future__ import annotations

from typing import List, Optional

from fastapi import FastAPI
from pydantic import BaseModel
import pandas as pd

from lap_stroke_pipeline import run_pipeline_from_df


app = FastAPI(title="Swim Metrics API", version="0.1.0")


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class Sample(BaseModel):
    """Single IMU sample.

    The field names are chosen to match your SwimData schema and the
    expectations of lap_stroke_pipeline.run_pipeline_from_df, after we
    convert timestamp_ms to a pandas datetime column.
    """

    timestamp_ms: int
    accel_x: float
    accel_y: float
    accel_z: float
    gyro_x: float
    gyro_y: float
    gyro_z: float
    stroke_type: Optional[str] = None


class SessionRequest(BaseModel):
    session_id: Optional[int] = None
    swimmer_id: Optional[int] = None
    exercise_id: Optional[int] = None
    pool_length_m: float = 50.0
    samples: List[Sample]


class LapOut(BaseModel):
    lap_number: int
    lap_time_s: float
    stroke_count: int
    velocity_m_per_s: float
    stroke_rate_hz: float
    stroke_rate_spm: float
    stroke_length_m: float
    stroke_index: float
    stroke_type: Optional[str] = None


class SessionAveragesOut(BaseModel):
    lap_count: int
    stroke_count: float
    avg_lap_time_s: float
    avg_velocity_m_per_s: float
    avg_stroke_rate_hz: float
    avg_stroke_length_m: float
    avg_stroke_index: float


class MetricsResponse(BaseModel):
    session_id: Optional[int]
    swimmer_id: Optional[int]
    exercise_id: Optional[int]
    session_averages: SessionAveragesOut
    laps: List[LapOut]


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------


def _build_dataframe_from_request(req: SessionRequest) -> pd.DataFrame:
    """Convert incoming JSON samples into the DataFrame expected by the pipeline."""

    if not req.samples:
        # Empty DataFrame with required columns so the pipeline can fail fast / predictably
        return pd.DataFrame(
            columns=[
                "timestamp",
                "accel_x",
                "accel_y",
                "accel_z",
                "gyro_x",
                "gyro_y",
                "gyro_z",
                "stroke_type",
            ]
        )

    data = [
        {
            "timestamp": s.timestamp_ms,
            "accel_x": s.accel_x,
            "accel_y": s.accel_y,
            "accel_z": s.accel_z,
            "gyro_x": s.gyro_x,
            "gyro_y": s.gyro_y,
            "gyro_z": s.gyro_z,
            "stroke_type": s.stroke_type,
        }
        for s in req.samples
    ]

    df = pd.DataFrame(data)

    # The pipeline expects a `datetime` column; convert from ms epoch.
    df["datetime"] = pd.to_datetime(df["timestamp"], unit="ms", utc=True)

    return df


# ---------------------------------------------------------------------------
# FastAPI endpoint
# ---------------------------------------------------------------------------


@app.post("/metrics/session", response_model=MetricsResponse)
def compute_metrics(req: SessionRequest) -> MetricsResponse:
    """Run the full lap + stroke pipeline for one session.

    This simply wraps `run_pipeline_from_df` from lap_stroke_pipeline.py and
    normalizes its output into a JSON shape that is easy for Android to
    consume.
    """

    df = _build_dataframe_from_request(req)

    per_lap_results, session_averages = run_pipeline_from_df(df)

    # Map Python pipeline dicts into LapOut and SessionAveragesOut
    laps: List[LapOut] = []
    for lap in per_lap_results:
        laps.append(
            LapOut(
                lap_number=int(lap["lap_number"]),
                lap_time_s=float(lap["lap_time"]),
                stroke_count=int(lap["stroke_count"]),
                velocity_m_per_s=float(lap["velocity"]),
                stroke_rate_hz=float(lap["stroke_rate_s"]),
                stroke_rate_spm=float(lap["stroke_rate_min"]),
                stroke_length_m=float(lap["stroke_length"]),
                stroke_index=float(lap["stroke_index"]),
                stroke_type=lap.get("stroke_type"),
            )
        )

    avg = SessionAveragesOut(
        lap_count=len(laps),
        stroke_count=float(session_averages.get("avg_stroke_count", 0.0)),
        avg_lap_time_s=float(session_averages.get("avg_lap_time", 0.0)),
        avg_velocity_m_per_s=float(session_averages.get("avg_velocity", 0.0)),
        avg_stroke_rate_hz=float(session_averages.get("avg_stroke_rate", 0.0)),
        avg_stroke_length_m=float(session_averages.get("avg_stroke_length", 0.0)),
        avg_stroke_index=float(session_averages.get("avg_stroke_index", 0.0)),
    )

    return MetricsResponse(
        session_id=req.session_id,
        swimmer_id=req.swimmer_id,
        exercise_id=req.exercise_id,
        session_averages=avg,
        laps=laps,
    )
