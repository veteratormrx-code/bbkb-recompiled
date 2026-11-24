from PIL import Image
im = Image.open(r'Z:\BBK\docs\testing\artifacts\probe\s.png').convert('RGB')
w, h = im.size
print('size', im.size)
for y in range(0, h, 120):
    row = [im.getpixel((x, y)) for x in range(0, w, 120)]
    uniq = {}
    for px in row:
        uniq[px] = uniq.get(px, 0) + 1
    print(y, sorted(uniq.items(), key=lambda kv: -kv[1])[:3])
