from sqlalchemy import create_engine, Table, select, update
from sqlalchemy.orm import sessionmaker, declarative_base

import random
import numpy as np
import tensorflow as tf
from tensorflow import keras
from librosa import feature, load
from librosa.util import pad_center
import sys
import os
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
import warnings
warnings.filterwarnings("ignore")

cycle_time = 6

diseases_types = ['Астма',
 'Бронхоэктазы ',
 'Бронхиолит',
 'Хроническая обструктивная болезнь легких',
 'Здоровый',
 'Инфекция нижних дыхательных путей',
 'Пневмония',
 'Инфекция верхних дыхательных путей'
]

DATABASE_URL = "postgresql://azatnv:azatnv@localhost:5432/azatnv_db"

Base = declarative_base()
engine, meta = create_engine(DATABASE_URL, echo = False), Base.metadata
meta.reflect(bind=engine)

ses = sessionmaker(bind = engine)
t_record = Table('t_record', meta, autoload_with = engine)

def add_diagnosis_info(location_path, diagnosis, probability, model_name):
    with ses() as session:
        query = update(t_record).where(t_record.c.location_path == location_path).\
            values(diagnosis = diagnosis, probability = probability, model_name = model_name).\
            execution_options(synchronize_session="fetch")
        result = session.execute(query)
        session.commit()

def getBreathCycle(raw_data, start, end, sr = 22050):
    max_ind = len(raw_data) 
    start_ind = min(int(start * sr), max_ind)
    end_ind = min(int(end * sr), max_ind)
    return raw_data[start_ind:end_ind]

def getFeatures(sound_arr, sample_rate):
    mfcc = feature.mfcc(y = sound_arr, sr = sample_rate)
    cstft = feature.chroma_stft(y = sound_arr, sr = sample_rate)
    mSpec = feature.melspectrogram(y = sound_arr, sr = sample_rate)
    return np.array([mfcc]), np.array([cstft]), np.array([mSpec])

if __name__ == "__main__":
    location_path = sys.argv[1]

    sound, sample_rate = load(location_path)
    duration = len(sound) / sample_rate

    diagnosis = ""
    probability = 0
    model_name = "model_v2.h5"

    model = keras.models.load_model(model_name)

    if duration < cycle_time:
        sound = pad_center(data = sound, size = cycle_time * sample_rate)
        
        mfcc, cstft, mSpec = getFeatures(sound, sample_rate)
        y = model.predict({"mfcc": mfcc,"croma": cstft,"mspec": mSpec}, verbose = 0)
        
        disease_index = np.argmax(y, axis = 1)[0]
        diagnosis = diseases_types[disease_index]
        probability = np.max(y, axis = 1)[0]
    else :
        list_diagnosis = []
        list_probabilities = []
        start = 0
        
        while start + cycle_time < duration and start + cycle_time < 30:
            pureSample = getBreathCycle(sound, start, start + cycle_time, sample_rate)
            start += cycle_time / 2
            
            mfcc,cstft,mSpec = getFeatures(pureSample, sample_rate)
            y = model.predict({"mfcc":mfcc,"croma":cstft,"mspec":mSpec}, verbose = 0)
            
            list_diagnosis.append(np.argmax(y, axis = 1)[0])
            list_probabilities.append(np.max(y, axis = 1)[0])
            
        disease_index = np.argmax(np.bincount(list_diagnosis))
        diagnosis = diseases_types[disease_index]
        probability = np.mean([x for i, x in list_probabilities if list_diagnosis[i] == diagnosis])
    
    add_diagnosis_info(location_path, diagnosis, probability.item(), model_name)
    print(diagnosis, probability.item(), model_name)