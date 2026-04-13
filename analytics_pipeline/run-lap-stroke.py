from lap_stroke_pipeline import run_pipeline_from_db
import pandas as pd


def format_per_lap(per_lap):
    df = pd.DataFrame(per_lap)
    if df.empty:
        print("No laps detected.")
        return

    # Round numeric columns for nicer display
    rounded = df.copy()
    rounded["lap_time"] = rounded["lap_time"].round(2)
    rounded["velocity"] = rounded["velocity"].round(3)
    rounded["stroke_rate_s"] = rounded["stroke_rate_s"].round(3)
    rounded["stroke_rate_min"] = rounded["stroke_rate_min"].round(1)
    rounded["stroke_length"] = rounded["stroke_length"].round(3)
    rounded["stroke_index"] = rounded["stroke_index"].round(3)

    # Rename columns with units for display only
    display_df = rounded.rename(columns={
        "lap_time": "lap_time_s",
        "velocity": "velocity_m_per_s",
        "stroke_rate_s": "stroke_rate_hz",
        "stroke_rate_min": "stroke_rate_spm",
        "stroke_length": "stroke_length_m",
        "stroke_index": "stroke_index_m2_per_s",
    })

    print("Per-lap metrics (units in column names):")
    print(display_df.to_string(index=False))


def format_session_averages(session_avgs):
    print("\nSession averages:")
    print(f"  avg_lap_time:     {session_avgs['avg_lap_time']:.2f} s")
    print(f"  avg_stroke_count: {session_avgs['avg_stroke_count']:.1f} strokes")
    print(f"  avg_velocity:     {session_avgs['avg_velocity']:.3f} m/s")
    print(f"  avg_stroke_rate:  {session_avgs['avg_stroke_rate']:.3f} Hz ({session_avgs['avg_stroke_rate']*60:.1f} spm)")
    print(f"  avg_stroke_length:{session_avgs['avg_stroke_length']:.3f} m/stroke")
    print(f"  avg_stroke_index: {session_avgs['avg_stroke_index']:.3f} m^2/s")


if __name__ == "__main__":
    per_lap, session_avgs = run_pipeline_from_db(
        db_path=r"test-1-feb-20-dani.db",
        table_name="sensor_data",
        stroke_type_col="stroke_type",
    )

    format_per_lap(per_lap)
    format_session_averages(session_avgs)
