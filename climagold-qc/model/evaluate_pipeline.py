"""Synthetic identifiers built from held-out public glyphs. NOT phone-photo accuracy."""
import argparse,json,struct,subprocess,os
from pathlib import Path
import numpy as np
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
import tensorflow as tf
p=argparse.ArgumentParser();p.add_argument('--trained',type=Path,required=True);p.add_argument('--java',required=True);p.add_argument('--classpath',required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--seed',type=int,default=7712026);p.add_argument('--per-prefix',type=int,default=100);a=p.parse_args()
a.out.mkdir(parents=True,exist_ok=True)
data=np.load(a.trained/'heldout.npz');x,y=data['x'],data['y'];labels='0123456789CEIKRWZ?';rng=np.random.default_rng(a.seed)
examples=[];images=[]
for prefix in ['C','K','IEW','RIEW','R','Z']:
 for n in range(a.per_prefix):
  number=''.join(str(d) for d in rng.integers(0,10,5));text=prefix+number;canvas=np.full((36,len(text)*32+8),255,np.uint8)
  for pos,c in enumerate(text):
   k=rng.choice(np.where(y==labels.index(c))[0]);glyph=(x[k,:,:,0]*255).astype(np.uint8)
   canvas[4:32,4+pos*32:32+pos*32]=255-glyph
  examples.append(text);images.append(canvas)
with (a.out/'images.bin').open('wb') as f:
 f.write(struct.pack('>i',len(images)))
 for image in images:
  h,w=image.shape;f.write(struct.pack('>ii',w,h));rgb=(0xff000000|image.astype(np.uint32)*0x010101).astype('>u4');f.write(rgb.tobytes())
subprocess.run([a.java,'-cp',a.classpath,'PipelineHarness','segment',str(a.out/'images.bin'),str(a.out/'glyphs.bin')],check=True)
interpreter=tf.lite.Interpreter(model_path=str(a.trained/'handwritten_glyphs.tflite'));interpreter.allocate_tensors();inp=interpreter.get_input_details()[0]['index'];out=interpreter.get_output_details()[0]['index']
with (a.out/'glyphs.bin').open('rb') as f,(a.out/'scores.bin').open('wb') as g:
 count=struct.unpack('>i',f.read(4))[0];g.write(struct.pack('>i',count))
 for i in range(count):
  n=struct.unpack('>i',f.read(4))[0];g.write(struct.pack('>i',n))
  for j in range(n):
   glyph=np.frombuffer(f.read(784*4),dtype='>f4').astype(np.float32).reshape(1,28,28,1)
   interpreter.set_tensor(inp,glyph);interpreter.invoke();scores=interpreter.get_tensor(out)[0];g.write(scores.astype('>f4').tobytes())
subprocess.run([a.java,'-cp',a.classpath,'PipelineHarness','decode',str(a.out/'scores.bin'),str(a.out/'results.bin')],check=True)
rows=[]
with (a.out/'results.bin').open('rb') as f:
 count=struct.unpack('>i',f.read(4))[0]
 for expected in examples:
  n=struct.unpack('>i',f.read(4))[0];candidates=[]
  for j in range(n):l=struct.unpack('>i',f.read(4))[0];candidates.append(f.read(l).decode())
  rows.append({'expected':expected,'candidates':candidates})
report={'seed':a.seed,'count':len(rows),'top1_accuracy':sum(bool(r['candidates']) and r['candidates'][0]==r['expected'] for r in rows)/len(rows),
 'top5_recall':sum(r['expected'] in r['candidates'] for r in rows)/len(rows),'no_candidate_rate':sum(not r['candidates'] for r in rows)/len(rows),
 'scope':'Synthetic clean single-line identifiers, assembled from held-out MNIST/EMNIST glyphs. Not phone photos; not field validation.', 'examples':rows}
(a.out/'pipeline_evaluation.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='examples'}))
