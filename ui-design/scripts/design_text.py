"""Measure the installed design font; avoid cutting CJK glyphs in static SVG fallbacks."""
import ctypes as C
from functools import lru_cache
p=C.CDLL('libpango-1.0.so.0');pc=C.CDLL('libpangocairo-1.0.so.0')
def bind(lib,name,args,result):
 f=getattr(lib,name);f.argtypes=args;f.restype=result;return f
fm=bind(pc,'pango_cairo_font_map_get_default',[],C.c_void_p)()
ctx=bind(p,'pango_font_map_create_context',[C.c_void_p],C.c_void_p)(fm)
layout=bind(p,'pango_layout_new',[C.c_void_p],C.c_void_p)(ctx)
new=bind(p,'pango_font_description_from_string',[C.c_char_p],C.c_void_p);setsize=bind(p,'pango_font_description_set_absolute_size',[C.c_void_p,C.c_double],None);setfont=bind(p,'pango_layout_set_font_description',[C.c_void_p,C.c_void_p],None);settext=bind(p,'pango_layout_set_text',[C.c_void_p,C.c_char_p,C.c_int],None);getsize=bind(p,'pango_layout_get_pixel_size',[C.c_void_p,C.POINTER(C.c_int),C.POINTER(C.c_int)],None);free=bind(p,'pango_font_description_free',[C.c_void_p],None)
@lru_cache(maxsize=4096)
def text_width(value,size):
 desc=new(b'Noto Sans CJK SC Semi-Bold');setsize(desc,float(size)*1024);setfont(layout,desc);settext(layout,str(value).encode(),-1);w=C.c_int();h=C.c_int();getsize(layout,C.byref(w),C.byref(h));free(desc);return w.value
