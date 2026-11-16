#!/usr/bin/env python3
"""Export PNG via the system's existing librsvg/Cairo; no packages required."""
from pathlib import Path
import ctypes as C
import xml.etree.ElementTree as ET
import sys

ROOT = Path(__file__).resolve().parents[1]
rsvg = C.CDLL('librsvg-2.so.2')
cairo = C.CDLL('libcairo.so.2')
gobject = C.CDLL('libgobject-2.0.so.0')

def bind(lib, name, args, result):
    f = getattr(lib, name)
    f.argtypes, f.restype = args, result
    return f

new = bind(rsvg,'rsvg_handle_new_from_file',[C.c_char_p,C.c_void_p],C.c_void_p)
render = bind(rsvg,'rsvg_handle_render_cairo',[C.c_void_p,C.c_void_p],C.c_int)
surface = bind(cairo,'cairo_image_surface_create',[C.c_int,C.c_int,C.c_int],C.c_void_p)
context = bind(cairo,'cairo_create',[C.c_void_p],C.c_void_p)
write = bind(cairo,'cairo_surface_write_to_png',[C.c_void_p,C.c_char_p],C.c_int)
destroy = bind(cairo,'cairo_destroy',[C.c_void_p],None)
finish = bind(cairo,'cairo_surface_destroy',[C.c_void_p],None)
unref = bind(gobject,'g_object_unref',[C.c_void_p],None)
scale = bind(cairo,'cairo_scale',[C.c_void_p,C.c_double,C.c_double],None)

if __name__ == '__main__':
    output = ROOT/'designs/previews'
    output.mkdir(exist_ok=True)
    files = [ROOT/'designs/svg'/f'{slug}.svg' for slug in sys.argv[1:]] if sys.argv[1:] else sorted((ROOT/'designs/svg').glob('*.svg'))
    for file in files:
        handle = new(str(file).encode(),None)
        if not handle: raise RuntimeError(f'Cannot load {file}')
        image = surface(0,1920,1080)
        cr = context(image)
        root = ET.parse(file).getroot()
        native_w,native_h = map(float,root.get('viewBox').split()[2:])
        scale(cr,1920/native_w,1080/native_h)
        if not render(handle,cr): raise RuntimeError(f'Cannot render {file}')
        status = write(image,str(output/(file.stem+'.png')).encode())
        destroy(cr); finish(image); unref(handle)
        if status: raise RuntimeError(f'PNG export failed: {file}')
        if (native_w,native_h)==(3840,2160):
            native_output = ROOT/'designs/native-4k'
            native_output.mkdir(exist_ok=True)
            handle = new(str(file).encode(),None)
            image = surface(0,3840,2160);cr = context(image)
            if not render(handle,cr): raise RuntimeError(f'Cannot render native 4K: {file}')
            status = write(image,str(native_output/(file.stem+'.png')).encode())
            destroy(cr);finish(image);unref(handle)
            if status: raise RuntimeError(f'Native PNG export failed: {file}')
    print(f'Exported {len(files)} SVGs at 1920 × 1080 using each native viewBox.')
