#!/usr/bin/env python3
"""Export PNG via the system's existing librsvg/Cairo; no packages required."""
from pathlib import Path
import ctypes as C

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

if __name__ == '__main__':
    output = ROOT/'designs/previews'
    output.mkdir(exist_ok=True)
    for file in sorted((ROOT/'designs/svg').glob('*.svg')):
        handle = new(str(file).encode(),None)
        if not handle: raise RuntimeError(f'Cannot load {file}')
        image = surface(0,1920,1080)
        cr = context(image)
        if not render(handle,cr): raise RuntimeError(f'Cannot render {file}')
        status = write(image,str(output/(file.stem+'.png')).encode())
        destroy(cr); finish(image); unref(handle)
        if status: raise RuntimeError(f'PNG export failed: {file}')
    print('Exported all SVGs at 1920 × 1080.')
