import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestClassifier
import pickle
import os

print("Generating V2 Synthetic Dataset...")
np.random.seed(42)
num_samples = 1000

# 1. Generate Fake Features (11 inputs)
screen_time = np.random.uniform(1, 12, num_samples) # hours
unlock_count = np.random.randint(10, 200, num_samples)
night_ratio = np.random.uniform(0.0, 1.0, num_samples)
scrolling_speed_avg = np.random.uniform(10, 200, num_samples)
scroll_erraticness = np.random.uniform(0.0, 1.5, num_samples) # 0=smooth, >1=jerky
app_switch_count_per_hour = np.random.randint(2, 60, num_samples)
typing_cps = np.random.uniform(1.0, 15.0, num_samples)
typing_hesitation_ms = np.random.uniform(100, 1500, num_samples)
typing_pauses_count = np.random.randint(0, 50, num_samples)
max_typing_pause_ms = np.random.uniform(1000, 10000, num_samples)
notification_response_sec = np.random.uniform(1.0, 300.0, num_samples)

# 2. Logic to determine V2 Targets
stress_level = []
anxiety_detected = []
burnout_detected = []
addiction_detected = []

for i in range(num_samples):
    # Stress: High if erratic scroll, high unlocks, fast typing
    stress_score = 0
    if scroll_erraticness[i] > 0.8: stress_score += 2
    if unlock_count[i] > 100: stress_score += 1
    if typing_cps[i] > 8: stress_score += 1
    
    if stress_score >= 3:
        stress_level.append("High")
    elif stress_score == 2:
        stress_level.append("Medium")
    else:
        stress_level.append("Low")
        
    # Anxiety: True if high-arousal (erratic scroll, fast response, lots of app switching, fast typing)
    if scroll_erraticness[i] > 1.0 and app_switch_count_per_hour[i] > 30 and notification_response_sec[i] < 30:
        anxiety_detected.append(True)
    else:
        anxiety_detected.append(False)
        
    # Burnout: True if low-engagement (slow typing, long hesitations, long pauses, high screen time, slow response)
    if typing_hesitation_ms[i] > 1000 and max_typing_pause_ms[i] > 5000 and notification_response_sec[i] > 120 and screen_time[i] > 6:
        burnout_detected.append(True)
    else:
        burnout_detected.append(False)
        
    # Addiction: True if high screen time, extreme unlocks, high night ratio
    if screen_time[i] > 8 and unlock_count[i] > 120 and night_ratio[i] > 0.5:
        addiction_detected.append(True)
    else:
        addiction_detected.append(False)

# Create DataFrame
df = pd.DataFrame({
    'screen_time': screen_time,
    'unlock_count': unlock_count,
    'night_ratio': night_ratio,
    'scrolling_speed_avg': scrolling_speed_avg,
    'scroll_erraticness': scroll_erraticness,
    'app_switch_count_per_hour': app_switch_count_per_hour,
    'typing_cps': typing_cps,
    'typing_hesitation_ms': typing_hesitation_ms,
    'typing_pauses_count': typing_pauses_count,
    'max_typing_pause_ms': max_typing_pause_ms,
    'notification_response_sec': notification_response_sec,
    
    'target_stress': stress_level,
    'target_anxiety': anxiety_detected,
    'target_burnout': burnout_detected,
    'target_addiction': addiction_detected
})

print(f"Dataset generated. Shape: {df.shape}")
df.to_csv('synthetic_behavior_data_v2.csv', index=False)

# 3. Train the 4 Classifiers
features = [
    'screen_time', 'unlock_count', 'night_ratio', 'scrolling_speed_avg', 
    'scroll_erraticness', 'app_switch_count_per_hour', 'typing_cps', 
    'typing_hesitation_ms', 'typing_pauses_count', 'max_typing_pause_ms', 
    'notification_response_sec'
]
X = df[features]

models = {}

print("\nTraining Stress Classifier...")
clf_stress = RandomForestClassifier(n_estimators=100, random_state=42)
clf_stress.fit(X, df['target_stress'])
models['stress'] = clf_stress

print("Training Anxiety Classifier...")
clf_anxiety = RandomForestClassifier(n_estimators=100, random_state=42)
clf_anxiety.fit(X, df['target_anxiety'])
models['anxiety'] = clf_anxiety

print("Training Burnout Classifier...")
clf_burnout = RandomForestClassifier(n_estimators=100, random_state=42)
clf_burnout.fit(X, df['target_burnout'])
models['burnout'] = clf_burnout

print("Training Addiction Classifier...")
clf_addiction = RandomForestClassifier(n_estimators=100, random_state=42)
clf_addiction.fit(X, df['target_addiction'])
models['addiction'] = clf_addiction

print("\nAll 4 Models trained successfully!")

# 4. Save the Ensemble Dictionary
model_filename = 'behavioral_ensemble_v2.pkl'
with open(model_filename, 'wb') as file:
    pickle.dump(models, file)

print(f"Model Ensemble saved to {model_filename}")
