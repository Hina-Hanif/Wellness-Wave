from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import Optional
import firebase_admin
from firebase_admin import credentials, firestore
import os
from datetime import datetime
import pickle
import pandas as pd

# Initialize FastAPI
app = FastAPI(title="Wellness Wave API")

# Setup Firebase (Placeholder for actual key JSON)
# For local dev, we assume serviceAccountKey.json is present
try:
    if not firebase_admin._apps:
        cred = credentials.Certificate("serviceAccountKey.json")
        firebase_admin.initialize_app(cred)
    db = firestore.client()
except Exception as e:
    print(f"Firebase initialization failed: {e}. Running in mock mode.")
    db = None

# Load ML Model
try:
    with open("behavioral_ensemble_v2.pkl", "rb") as f:
        ml_models = pickle.load(f)
    print("V2 AI Model Ensemble loaded successfully!")
except Exception as e:
    print(f"ML Model failed to load: {e}")
    ml_models = None

class UsageMetrics(BaseModel):
    user_id: str
    date: str
    screen_time: int
    unlock_count: int
    social_time: int
    productivity_time: int
    night_usage: int
    night_ratio: float
    session_count: int
    scrolling_speed_avg: float
    scroll_erraticness: float
    typing_cps: float
    typing_hesitation_ms: int
    backspace_rate: float
    notification_response_sec: float
    app_switch_count_per_hour: int
    usage_consistency_shift: float
    typing_pauses_count: int
    max_typing_pause_ms: int
    mood_score: int

@app.get("/health")
def health_check():
    return {"status": "active", "message": "Wellness Wave API is running smoothly."}

@app.post("/daily-data")
async def submit_daily_data(metrics: UsageMetrics):
    if not db:
        return {"status": "mock_success", "message": "Firebase not connected, data not stored."}
    
    try:
        doc_ref = db.collection("users").document(metrics.user_id).collection("daily_metrics").document(metrics.date)
        doc_ref.set(metrics.dict())
        return {"status": "success", "message": "Daily metrics stored securely."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/prediction")
async def get_prediction(user_id: str):
    if not ml_models:
        return {
            "user_id": user_id,
            "date_evaluated": datetime.now().strftime("%Y-%m-%d"),
            "stress_level": "Medium",
            "anxiety_detected": False,
            "burnout_detected": False,
            "addiction_detected": False,
            "real_time_feedback": "Model offline. Returning default state."
        }
    
    # Try to fetch latest data from Firebase. If no db, use default baseline.
    features = {
        'screen_time': [4.0], 'unlock_count': [30], 'night_ratio': [0.1], 
        'scrolling_speed_avg': [40.0], 'scroll_erraticness': [0.3], 
        'app_switch_count_per_hour': [10], 'typing_cps': [5.0], 
        'typing_hesitation_ms': [300], 'typing_pauses_count': [5], 
        'max_typing_pause_ms': [2000], 'notification_response_sec': [15.0]
    }
    
    if db:
        try:
            docs = db.collection("users").document(user_id).collection("daily_metrics").order_by("date", direction=firestore.Query.DESCENDING).limit(1).stream()
            metrics = next(docs, None)
            if metrics:
                data = metrics.to_dict()
                features = {
                    'screen_time': [data.get('screen_time', 4.0)],
                    'unlock_count': [data.get('unlock_count', 30)],
                    'night_ratio': [data.get('night_ratio', 0.1)],
                    'scrolling_speed_avg': [data.get('scrolling_speed_avg', 40.0)],
                    'scroll_erraticness': [data.get('scroll_erraticness', 0.3)],
                    'app_switch_count_per_hour': [data.get('app_switch_count_per_hour', 10)],
                    'typing_cps': [data.get('typing_cps', 5.0)],
                    'typing_hesitation_ms': [data.get('typing_hesitation_ms', 300)],
                    'typing_pauses_count': [data.get('typing_pauses_count', 5)],
                    'max_typing_pause_ms': [data.get('max_typing_pause_ms', 2000)],
                    'notification_response_sec': [data.get('notification_response_sec', 15.0)]
                }
        except Exception as e:
            print(f"Error fetching data from Firebase: {e}")
            
    # Perform Prediction
    df_features = pd.DataFrame(features)
    
    stress_pred = ml_models['stress'].predict(df_features)[0]
    anxiety_pred = bool(ml_models['anxiety'].predict(df_features)[0])
    burnout_pred = bool(ml_models['burnout'].predict(df_features)[0])
    addiction_pred = bool(ml_models['addiction'].predict(df_features)[0])
    
    # Generate dynamic feedback based on combination
    feedback = "Metrics indicate steady, calm usage. Keep it up!"
    if burnout_pred:
        feedback = "Critical burnout risk detected. Complete disconnect is advised."
    elif anxiety_pred:
        feedback = "High arousal patterns detected. Try a 2-minute breathing exercise."
    elif addiction_pred:
        feedback = "Screen time and unlock intensity are very high. Consider setting app limits."
    elif stress_pred == "High":
        feedback = "Elevated stress detected. Please take a break."
        
    return {
        "user_id": user_id,
        "date_evaluated": datetime.now().strftime("%Y-%m-%d"),
        "stress_level": stress_pred,
        "anxiety_detected": anxiety_pred,
        "burnout_detected": burnout_pred,
        "addiction_detected": addiction_pred,
        "real_time_feedback": feedback
    }

@app.get("/history")
async def get_history(user_id: str):
    # This will be replaced by actual historical queries from Firebase
    import random
    from datetime import timedelta
    
    today = datetime.now()
    history = []
    
    # Generate 7 days of mock history
    for i in range(6, -1, -1):
        past_date = today - timedelta(days=i)
        
        # Adding slight randomization for a realistic looking graph
        stress_score = max(0.2, min(0.9, 0.4 + random.uniform(-0.15, 0.3)))
        screen_time_hours = max(2.0, min(8.0, 4.5 + random.uniform(-1.0, 2.5)))
        
        history.append({
            "date": past_date.strftime("%Y-%m-%d"),
            "stress_score": round(stress_score, 2),
            "screen_time_hours": round(screen_time_hours, 1)
        })
        
    return {
        "user_id": user_id,
        "history": history
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
