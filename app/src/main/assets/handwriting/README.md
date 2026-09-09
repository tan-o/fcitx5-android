# Handwriting model

`handwriting-zh_CN.model` is not committed here (26 MB binary). Drop it in
this directory before building; without it the handwriting window only shows
a "model not installed" notice.

    curl -Lo app/src/main/assets/handwriting/handwriting-zh_CN.model \
      https://raw.githubusercontent.com/alexrao/zinnia-handwriting/master/res/handwriting-zh_CN.model

It is the Tegaki/zinnia simplified-chinese model: 6763 characters (GB2312
level 1 + 2), format version 1, recognized by the vendored zinnia under
`app/src/main/cpp/zinnia` (BSD).

`pinyin.txt` annotates candidates with the most common reading of each of the
8105 characters of 通用规范汉字表, generated from
[mozillazg/pinyin-data](https://github.com/mozillazg/pinyin-data)
(`kMandarin_8105.txt`).
