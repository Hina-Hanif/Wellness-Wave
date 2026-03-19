import os
import json
import joblib
import pandas as pd
from fastapi import APIRouter, HTTPException
from database import get_latest_metrics

router = APIRouter()

BASE_DIR = os.path.dirname(os.path.dirname(__file__))

MODEL_PATH = os.path.join(BASE_DIR, "stress_model.pkl")
PREPROCESSOR_PATH = os.path.join(BASE_DIR, "preprocessor.pkl")
ENCODER_PATH = os.path.join(BASE_DIR, "label_encoder.pkl")
META_PATH = os.path.join(BASE_DIR, "model_meta.json")

# Load files safely
print(f"Starting model load pipeline...")
try:
    print(f"Loading model from: {MODEL_PATH}")
    model = joblib.load(MODEL_PATH)
    print(f"Loading preprocessor from: {PREPROCESSOR_PATH}")
    preprocessor = joblib.load(PREPROCESSOR_PATH)
    print(f"Loading label encoder from: {ENCODER_PATH}")
    label_encoder = joblib.load(ENCODER_PATH)

    print(f"Loading metadata from: {META_PATH}")
    with open(META_PATH, "r") as f:
        meta = json.load(f)

    features_list = meta["features"]
    print("All models and metadata loaded successfully.")

except Exception as e:
    print(f"MODEL LOAD ERROR: {type(e).__name__} - {e}")
    print("Possible file corruption or invalid file format. Please check the model files.")
    model = None
    preprocessor = None
    label_encoder = None
    features_list = None

@router.get("/prediction")
async def get_prediction(user_id: str):
    latest_data = get_latest_metrics(user_id)

    if not latest_data:
        return {
            "user_id": user_id,
            "stress_level": "Unknown",
            "confidence_score": 0.0,
            "real_time_feedback": "Insufficient data."
        }

    if model is None or features_list is None or preprocessor is None or label_encoder is None:
        return {
            "user_id": user_id,
            "stress_level": "Model offline",
            "confidence_score": 0.0,
            "real_time_feedback": "Model loading failed. Please check backend logs."
        }

    try:
        input_dict = {feature: latest_data.get(feature, 0.0) for feature in features_list}

        screen_time = latest_data.get("screen_time", 0.0)

        input_dict["social_ratio"] = latest_data.get("social_time", 0.0) / (screen_time + 1e-6)
        input_dict["night_ratio"] = latest_data.get("night_usage", 0.0) / (screen_time + 1e-6)
        input_dict["productivity_ratio"] = latest_data.get("productivity_time", 0.0) / (screen_time + 1e-6)

        input_dict["switch_per_hour"] = screen_time / 60.0

        if "app_switch_count" in latest_data:
            input_dict["switch_per_hour"] = latest_data["app_switch_count"] / (screen_time / 60.0 + 1e-6)

        input_dict["pause_per_keystroke"] = latest_data.get("typing_pauses", 0.0) / (latest_data.get("session_count", 0.0) + 1e-6)

        ordered_input = [input_dict.get(feat, 0.0) for feat in features_list]
        df = pd.DataFrame([ordered_input], columns=features_list)

        X_processed = preprocessor.transform(df)

        pred_encoded = model.predict(X_processed)[0]
        prediction = label_encoder.inverse_transform([pred_encoded])[0]

        if hasattr(model, "predict_proba"):
            probabilities = model.predict_proba(X_processed)[0]
            confidence = float(max(probabilities))
        else:
            confidence = 1.0

        return {
            "user_id": user_id,
            "stress_level": str(prediction),
            "confidence_score": confidence,
            "real_time_feedback": "Prediction generated successfully."
        }

    except Exception as e:
        print("PREDICTION ERROR:", e)
        raise HTTPException(status_code=500, detail=str(e))