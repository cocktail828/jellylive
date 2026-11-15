# Markwon 4.6.2 StrikeHandler checks this optional extension with Class.forName
# and falls back to Android's StrikethroughSpan when it is absent.
# https://github.com/noties/Markwon/blob/v4.6.2/markwon-html/src/main/java/io/noties/markwon/html/tag/StrikeHandler.java
-dontwarn org.commonmark.ext.gfm.strikethrough.Strikethrough
