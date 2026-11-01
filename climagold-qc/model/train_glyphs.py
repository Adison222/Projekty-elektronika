"""Reproducible small on-device classifier. No user photographs are used or uploaded."""
import os
os.environ.setdefault('TF_USE_LEGACY_KERAS', '1')
os.environ.setdefault('TF_NUM_INTRAOP_THREADS', '3')
os.environ.setdefault('TF_NUM_INTEROP_THREADS', '1')
import argparse, gzip, hashlib, json, struct
from pathlib import Path
import numpy as np
from PIL import Image
import tensorflow as tf

LABELS = '0123456789CEIKRWZ?'

def idx(path):
    with gzip.open(path, 'rb') as f: data=f.read()
    magic, count=struct.unpack('>II',data[:8])
    return np.frombuffer(data, np.uint8, offset=16 if magic==2051 else 8).reshape((count,28,28) if magic==2051 else (count,))

def normalize(image):
    ys,xs=np.where(image>32)
    out=np.zeros((28,28),np.uint8)
    if not len(xs): return out
    box=image[ys.min():ys.max()+1,xs.min():xs.max()+1]
    h,w=box.shape
    factor=20/max(h,w)
    nw,nh=max(1,round(w*factor)),max(1,round(h*factor))
    glyph=np.asarray(Image.fromarray(box).resize((nw,nh),Image.Resampling.BILINEAR))
    out[(28-nh)//2:(28-nh)//2+nh,(28-nw)//2:(28-nw)//2+nw]=glyph
    return out

def load(root, train):
    split='train' if train else 'test'
    letter_x=idx(root/f'emnist-letters-{split}-images-idx3-ubyte.gz').transpose(0,2,1)
    letter_y=idx(root/f'emnist-letters-{split}-labels-idx1-ubyte.gz')
    split='train' if train else 't10k'
    digit_x=idx(root/f'{split}-images-idx3-ubyte.gz')
    digit_y=idx(root/f'{split}-labels-idx1-ubyte.gz')
    rng=np.random.default_rng(20261101)
    xs=[digit_x]; ys=[digit_y]
    selected=set()
    for label in LABELS[10:-1]:
        code=ord(label)-64; selected.add(code)
        mask=letter_y==code
        xs.append(letter_x[mask]); ys.append(np.full(mask.sum(),LABELS.index(label),np.uint8))
    other=np.where(~np.isin(letter_y,list(selected)))[0]
    sample=rng.choice(other, min(len(other),4800 if train else 800),replace=False)
    xs.append(letter_x[sample]); ys.append(np.full(len(sample),17,np.uint8))
    x=np.concatenate(xs); y=np.concatenate(ys)
    x=np.stack([normalize(im) for im in x])[...,None].astype(np.float32)/255
    return x,y

def main():
    p=argparse.ArgumentParser();p.add_argument('--data',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--epochs',type=int,default=6);a=p.parse_args()
    tf.keras.utils.set_random_seed(20261101)
    x,y=load(a.data,True); tx,ty=load(a.data,False)
    rng=np.random.default_rng(20261101); order=rng.permutation(len(y)); split=int(len(y)*.9)
    train,valid=order[:split],order[split:]
    print('train',len(train),'validation',len(valid),'test',len(ty),flush=True)
    inputs=tf.keras.Input((28,28,1))
    v=tf.keras.layers.RandomRotation(.025,fill_mode='constant',seed=41)(inputs)
    v=tf.keras.layers.RandomTranslation(.045,.045,fill_mode='constant',seed=42)(v)
    v=tf.keras.layers.Conv2D(24,3,padding='same',activation='relu',name='conv1',kernel_initializer=tf.keras.initializers.GlorotUniform(seed=43))(v)
    v=tf.keras.layers.MaxPool2D(name='pool1')(v)
    v=tf.keras.layers.Conv2D(48,3,padding='same',activation='relu',name='conv2',kernel_initializer=tf.keras.initializers.GlorotUniform(seed=44))(v)
    v=tf.keras.layers.MaxPool2D(name='pool2')(v)
    v=tf.keras.layers.Flatten(name='flat')(v)
    v=tf.keras.layers.Dense(128,activation='relu',name='features',kernel_initializer=tf.keras.initializers.GlorotUniform(seed=45))(v)
    v=tf.keras.layers.Dropout(.15,seed=46)(v)
    digit=tf.keras.layers.Dense(10,activation='softmax',name='digits',kernel_initializer=tf.keras.initializers.GlorotUniform(seed=47))(v)
    letter=tf.keras.layers.Dense(8,activation='softmax',name='letters',kernel_initializer=tf.keras.initializers.GlorotUniform(seed=48))(v)
    kind=tf.keras.layers.Dense(2,activation='softmax',name='kind',kernel_initializer=tf.keras.initializers.GlorotUniform(seed=49))(v)
    model=tf.keras.Model(inputs,[digit,letter,kind])
    model.compile(optimizer=tf.keras.optimizers.Adam(.001),loss=['sparse_categorical_crossentropy']*3,loss_weights=[1,1,.3])
    def targets(yy):return [np.minimum(yy,9),np.maximum(yy.astype(int)-10,0),(yy>=10).astype(int)]
    def weights(yy):return [(yy<10).astype(float),(yy>=10).astype(float),np.ones(len(yy))]
    history=model.fit(x[train],targets(y[train]),sample_weight=weights(y[train]),
        validation_data=(x[valid],targets(y[valid]),weights(y[valid])),epochs=a.epochs,batch_size=256,verbose=2,
        callbacks=[tf.keras.callbacks.ReduceLROnPlateau(patience=2,factor=.5),tf.keras.callbacks.EarlyStopping(patience=4,restore_best_weights=True)])
    pd,pl,pk=model.predict(tx,batch_size=256,verbose=0)
    pred=np.concatenate([pd*pk[:,0:1],pl*pk[:,1:2]],axis=1)
    report={'architecture':'shared CNN with separately supervised digit, letter and kind heads',
      'labels':LABELS,'train_count':len(train),'validation_count':len(valid),'test_count':len(ty),'epochs_completed':len(history.history['loss']),
      'test_accuracy':float(np.mean(pred.argmax(1)==ty)),
      'known_character_accuracy':float(np.mean(pred[ty!=17].argmax(1)==ty[ty!=17])),
      'digit_accuracy_given_digit':float(np.mean(pd[ty<10].argmax(1)==ty[ty<10])),
      'letter_accuracy_given_known_letter':float(np.mean(pl[(ty>=10)&(ty<17)].argmax(1)==ty[(ty>=10)&(ty<17)]-10)),
      'per_class':{c:{'count':int((ty==i).sum()),'accuracy':float(np.mean(pred[ty==i].argmax(1)==i))} for i,c in enumerate(LABELS)},
      'scope':'Isolated normalized public dataset glyphs; not accuracy on real phone photos or full identifiers.',
      'sources_sha256':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(a.data.glob('*.gz'))}}
    # Strip training-only augmentation/dropout by copying just inference layers.
    ii=tf.keras.Input((28,28,1));vv=ii
    for name in ['conv1','pool1','conv2','pool2','flat','features']:vv=model.get_layer(name)(vv)
    dd=model.get_layer('digits')(vv);ll=model.get_layer('letters')(vv);kk=model.get_layer('kind')(vv)
    oo=tf.keras.layers.Concatenate()([dd*kk[:,0:1],ll*kk[:,1:2]])
    inf=tf.keras.Model(ii,oo)
    converter=tf.lite.TFLiteConverter.from_keras_model(inf)
    converter.optimizations=[tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types=[tf.float16]
    binary=converter.convert();a.out.mkdir(parents=True,exist_ok=True)
    (a.out/'handwritten_glyphs.tflite').write_bytes(binary)
    report['model_sha256']=hashlib.sha256(binary).hexdigest();report['model_bytes']=len(binary)
    (a.out/'evaluation.json').write_text(json.dumps(report,indent=2)+'\n')
    interpreter=tf.lite.Interpreter(model_content=binary);interpreter.allocate_tensors()
    samples=[]
    for i,c in enumerate(LABELS[:-1]):
        k=int(np.where(ty==i)[0][0]);interpreter.set_tensor(interpreter.get_input_details()[0]['index'],tx[k:k+1]);interpreter.invoke()
        result=interpreter.get_tensor(interpreter.get_output_details()[0]['index'])[0]
        samples.append({'label':c,'pixels':(tx[k,:,:,0]*255).astype(np.uint8).reshape(-1).tolist(),'scores':result.tolist()})
    (a.out/'reference_glyphs.json').write_text(json.dumps(samples))
    np.savez_compressed(a.out/'heldout.npz',x=tx,y=ty)
    print(json.dumps(report),flush=True)
if __name__=='__main__':main()
