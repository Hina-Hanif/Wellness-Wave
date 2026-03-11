import os
import joblib
import pandas as pd
from fastapi import APIRouter, HTTPException
from wellness_wave_backend.database import get_latest_metrics

router = APIRouter()

MODEL_PATH = "wellness_wave_backend/behavioral_model.pkl"
FEATURES_PATH = "wellness_wave_backend/model_features.pkl"

# Load model and features on startup if available
model = None
features_list = None
if os.path.exists(MODEL_PATH) and os.path.exists(FEATURES_PATH):
    model = joblib.load(MODEL_PATH)
    features_list = joblib.load(FEATURES_PATH)

@router.get("/prediction")
async def get_prediction(user_id: str):
    latest_data = get_latest_metrics(user_id)
    
    if not latest_data:
        return {
            "user_id": user_id,
            "stress_level": "Unknown",
            "confidence_score": 0.0,
            "real_time_feedback": "Insufficient data. Keep tracking your behavior!"
        }

    # If model is not loaded (e.g., during testing before training), fallback to mock logic
    if model is None or features_list is None:
        return {
            "user_id": user_id,
            "date_evaluated": latest_data.get("date"),
            "stress_level": "Balanced" if latest_data.get("mood_score", 5) > 5 else "Stressed",
            "confidence_score": 0.5,
            "real_time_feedback": "Basic feedback active. Train the model for advanced insights."
        }

    # Prepare features for prediction
    try:
        # 1. Start with the raw features we already have
        input_dict = {feature: latest_data.get(feature, 0.0) for feature in features_list}
        
        # 2. Add engineered features that the model expects (V3 upgrade)
        screen_time = latest_data.get("screen_time", 0.0)
        input_dict["social_ratio"] = latest_data.get("social_time", 0.0) / (screen_time + 1e-6)
        input_dict["night_ratio"] = latest_data.get("night_usage", 0.0) / (screen_time + 1e-6)
        input_dict["productivity_ratio"] = latest_data.get("productivity_time", 0.0) / (screen_time + 1e-6)
        input_dict["switch_per_hour"] = screen_time / 60.0 # Using screen time as proxy if app_switch_count is raw
        # If the model features list contains 'switch_per_hour', ensured it's calculated
        if "app_switch_count" in latest_data:
             input_dict["switch_per_hour"] = latest_data["app_switch_count"] / (screen_time / 60.0 + 1e-6)
        
        input_dict["pause_per_keystroke"] = latest_data.get("typing_pauses", 0.0) / (latest_data.get("session_count", 0.0) + 1e-6)
        
        # Ensure only the expected features are in the dataframe in the correct order
        ordered_input = [input_dict.get(feat, 0.0) for feat in features_list]
        df = pd.DataFrame([ordered_input], columns=features_list)
        
        # Make prediction
        prediction = model.predict(df)[0]
        
        # Get probabilities to determine confidence score
        probabilities = model.predict_proba(df)[0]
        confidence = float(max(probabilities))
        
        # Generate feedback based on category
        feedback_map = {
            "Stress": "High stress detected. Your scrolling patterns are erratic. Consider a 2-minute breathing break.",
            "Anxiety": "Signs of anxiety detected (high typing speed + frequent app switching). Try to focus on one task!",
            "Burnout": "Warning: Burnout pattern found. Very high night usage and screen time. Time to unplug.",
            "Addiction": "High digital dependency. Frequent unlocks and app-switching. Try a 'No-Phone Hour'.",
            "Balanced": "Pattern looks perfect! Healthy usage and consistent typing speed. You're in your flow state."
        }
        
        return {
            "user_id": user_id,
            "date_evaluated": latest_data.get("date"),
            "stress_level": prediction,
            "confidence_score": confidence,
            "anxiety_detected": prediction == "Anxiety",
            "burnout_detected": prediction == "Burnout",
            "addiction_detected": prediction == "Addiction",
            "real_time_feedback": feedback_map.get(prediction, "Keep monitoring your wellness.")
        }
    except Exception as e:
        print(f"PREDICTION ERROR: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Prediction error: {str(e)}")
