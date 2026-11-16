"""Preserve figure numbers while removing the cancelled font feature from the current pack."""
FONT_FIGURES={58,62,64,68,80,81,88,93,100,144}

def retire(screens,output):
    for number in FONT_FIGURES:
        screen=screens[number-1]
        screen.update(retired=True,note='字号调整功能已移除；保留原图号，不生成或展示旧稿。',flow='已移除')
        for folder,suffix in [('svg','svg'),('previews','png'),('native-4k','png')]:
            (output/folder/f"{screen['id']}.{suffix}").unlink(missing_ok=True)
