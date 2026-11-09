"""Geometry and type checks for native-TV design SVGs. Not Android device tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
import json,ctypes as C,ctypes.util
ROOT=Path(__file__).resolve().parents[1];D=ROOT/'designs'
p=C.CDLL(ctypes.util.find_library('pango-1.0'));pc=C.CDLL(ctypes.util.find_library('pangocairo-1.0'));go=C.CDLL(ctypes.util.find_library('gobject-2.0'))
def fn(lib,name,args,result):
 f=getattr(lib,name);f.argtypes=args;f.restype=result;return f
fm=fn(pc,'pango_cairo_font_map_get_default',[],C.c_void_p)();ctx=fn(p,'pango_font_map_create_context',[C.c_void_p],C.c_void_p)(fm);layout=fn(p,'pango_layout_new',[C.c_void_p],C.c_void_p)(ctx)
descNew=fn(p,'pango_font_description_from_string',[C.c_char_p],C.c_void_p);sizeSet=fn(p,'pango_font_description_set_absolute_size',[C.c_void_p,C.c_double],None);weightSet=fn(p,'pango_font_description_set_weight',[C.c_void_p,C.c_int],None)
fontSet=fn(p,'pango_layout_set_font_description',[C.c_void_p,C.c_void_p],None);textSet=fn(p,'pango_layout_set_text',[C.c_void_p,C.c_char_p,C.c_int],None);descFree=fn(p,'pango_font_description_free',[C.c_void_p],None)
class Box(C.Structure):_fields_=[('x',C.c_int),('y',C.c_int),('width',C.c_int),('height',C.c_int)]
getExt=fn(p,'pango_layout_get_pixel_extents',[C.c_void_p,C.POINTER(Box),C.POINTER(Box)],None);getBase=fn(p,'pango_layout_get_baseline',[C.c_void_p],C.c_int)
manifest=json.loads((D/'manifest.json').read_text());issues=[];measurements=[];focus=[]
for n,item in enumerate(manifest,1):
 f=D/'svg'/f"{item['id']}.svg"
 if item.get('retired'):
  assert not f.exists() and not (D/'previews'/f"{item['id']}.png").exists();continue
 tree=ET.parse(f);root=tree.getroot();parents={child:parent for parent in root.iter() for child in parent}
 assert root.get('viewBox')=='0 0 1920 1080'
 for e in root.iter():
  tag=e.tag.split('}')[-1]
  if tag=='rect' and e.get('stroke','').lower() in ('#fff','#ffffff'):
   x,y,w,h=[float(e.get(k,0)) for k in ('x','y','width','height')];border=float(e.get('stroke-width',1))/2
   if x-border<95.5 or x+w+border>1824.5 or y-border<53.5 or y+h+border>1026.5:focus.append({'figure':n,'box':[x,y,w,h],'stroke':border*2})
  if tag!='text':continue
  a=e;clipped=False
  while a is not None:
   if a.get('clip-path'):clipped=True
   a=parents.get(a)
  value=''.join(e.itertext());sz=float(e.get('font-size',28));x=float(e.get('x',0));y=float(e.get('y',0))
  desc=descNew(b'Noto Sans CJK SC');sizeSet(desc,sz*1024);weightSet(desc,int(e.get('font-weight',400)));fontSet(layout,desc);textSet(layout,value.encode(),-1)
  ink=Box();logical=Box();getExt(layout,C.byref(ink),C.byref(logical));baseline=getBase(layout)/1024;descFree(desc)
  origin=x-logical.width/2 if e.get('text-anchor')=='middle' else x-logical.width if e.get('text-anchor')=='end' else x
  bounds=[round(origin+ink.x,2),round(y+ink.y-baseline,2),round(origin+ink.x+ink.width,2),round(y+ink.y-baseline+ink.height,2)]
  entry={'figure':n,'text':value,'size':sz,'bounds':bounds,'clippedIntentionally':clipped};measurements.append(entry)
  if sz<24:issues.append(dict(entry,problem='below secondary type floor'))
  if not clipped and (bounds[0]<95.5 or bounds[2]>1824.5 or bounds[1]<53.5 or bounds[3]>1026.5):issues.append(dict(entry,problem='outside TV safe area'))
report={'scope':'SVG/Pango design-font geometry only; not Android runtime screenshots or focus execution','slots':len(manifest),'active':sum(not x.get('retired') for x in manifest),'textCount':len(measurements),'textIssues':issues,'focusIssues':focus,'measurements':measurements}
(D/'tv-design-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='measurements'},ensure_ascii=False,indent=2))
