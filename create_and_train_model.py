import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Dense, Input

def create_training_data(num_samples=10000):
    """
    Generates a synthetic dataset for training the smart mixer model.
    """
    print(f"Generating {num_samples} synthetic data samples...")
    
    # Modes: 0=Sleep, 1=Focus, 2=Relax
    modes = np.random.randint(0, 3, size=num_samples)
    
    # Time: 0-23 (hour of the day)
    times = np.random.randint(0, 24, size=num_samples)
    
    # Initialize output arrays
    rain_volume = np.zeros(num_samples)
    wind_volume = np.zeros(num_samples)
    thunder_chance = np.zeros(num_samples)
    bird_chance = np.zeros(num_samples)
    
    for i in range(num_samples):
        mode = modes[i]
        time = times[i]
        
        if mode == 0: # --- Mode: Sleep ---
            rain_volume[i] = np.random.uniform(0.7, 1.0)
            wind_volume[i] = np.random.uniform(0.1, 0.3)
            thunder_chance[i] = np.random.uniform(0.0, 0.05)
            # No birds at night, few in the day
            bird_chance[i] = 0.0 if (time < 6 or time > 20) else np.random.uniform(0.0, 0.05)
            
        elif mode == 1: # --- Mode: Focus ---
            rain_volume[i] = np.random.uniform(0.4, 0.6)
            wind_volume[i] = 0.0 # No wind for focus
            thunder_chance[i] = 0.0 # No thunder for focus
            bird_chance[i] = 0.0 # No birds for focus
            
        elif mode == 2: # --- Mode: Relax ---
            rain_volume[i] = np.random.uniform(0.2, 0.4)
            wind_volume[i] = np.random.uniform(0.3, 0.5)
            thunder_chance[i] = np.random.uniform(0.0, 0.1) # Occasional thunder
            # Birds only during the day
            bird_chance[i] = 0.0 if (time < 7 or time > 19) else np.random.uniform(0.2, 0.5)

    # Create DataFrame
    data = {
        'mode': modes,
        'time_of_day': times,
        'rain_volume': rain_volume,
        'wind_volume': wind_volume,
        'thunder_chance': thunder_chance,
        'bird_chance': bird_chance
    }
    df = pd.DataFrame(data)
    print("Dataset generated successfully.")
    return df

def train_model(df):
    """
    Trains a neural network on the provided DataFrame.
    """
    print("Training model...")
    
    # 1. Split Data
    X = df[['mode', 'time_of_day']].values
    y = df[['rain_volume', 'wind_volume', 'thunder_chance', 'bird_chance']].values
    
    X_train, X_val, y_train, y_val = train_test_split(X, y, test_size=0.2, random_state=42)
    
    # 2. Define Model
    model = Sequential([
        Input(shape=[2], name="input_layer"),
        Dense(16, activation='relu'),
        Dense(8, activation='relu'),
        Dense(4, activation='sigmoid', name="output_layer") # Sigmoid keeps outputs between 0 and 1
    ])
    
    # 3. Compile Model
    model.compile(
        optimizer='adam',
        loss='mean_squared_error' # Good for regression tasks
    )
    
    model.summary()
    
    # 4. Train Model
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=50, # 50 epochs is plenty for this simple data
        batch_size=32,
        verbose=1
    )
    
    print("Model training complete.")
    return model

def convert_to_tflite(model):
    """
    Converts the trained Keras model to a .tflite file.
    """
    print("Converting model to TensorFlow Lite format...")
    
    # 1. Create converter
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # 2. Convert
    tflite_model = converter.convert()
    
    # 3. Save the model
    with open('model.tflite', 'wb') as f:
        f.write(tflite_model)
        
    print("Successfully saved model as 'model.tflite'")

if __name__ == "__main__":
    # --- Run the full pipeline ---
    
    # Step 1: Create the data
    dataset = create_training_data()
    
    # Step 2: Train the model
    trained_model = train_model(dataset)
    
    # Step 3: Convert and save the model
    convert_to_tflite(trained_model)
    
    print("\n--- All steps complete. ---")
    print("You now have a 'model.tflite' file in this directory.")
