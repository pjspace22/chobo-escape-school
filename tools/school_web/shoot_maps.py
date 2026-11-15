# 초보 탈출 학교: 웹 3D 연습 맵 23개를 PC 크롬으로 찍어 앱 assets/school_web/maps/<카드id>.jpg 로 넣는다
#
# 0.19.0 현규 결정(11/10): 에뮬레이터 렉 때문에 앱 안의 3D 맵은 사진으로 바꾸고, 움직이는 연습은 웹 버전에서 개발.
# 원본: origin/main 의 materials/documents/plan-school-parking-sim-3d.html (웹 3D)
# 쓰는 법 (저장소 맨 위에서):  git fetch origin && python tools/school_web/shoot_maps.py
#   카메라는 "뒤에서", 1280x720 창에서 3D 부분만 자름 (오른쪽 설명 칸·아래 시점 버튼 뺌)
# 필요: Chrome, Pillow, 인터넷(웹 원본이 three.js를 CDN에서 받음)
import os, re, subprocess, sys, tempfile
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'school_web', 'maps')
REF = sys.argv[1] if len(sys.argv) > 1 else 'origin/main'
CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'

src = subprocess.run(['git', 'show', f'{REF}:materials/documents/plan-school-parking-sim-3d.html'], cwd=ROOT,
                     capture_output=True, check=True).stdout.decode('utf-8')
ids = re.findall(r'^ ([a-z_]+):\{t:\d', src[src.index('const MAPS={'):src.index('const TIER_NAME')], re.M)
# 열리면 "뒤에서" 시점 버튼을 눌러 둔다
click = ("<script>addEventListener('load',()=>setTimeout(()=>{[...document.querySelectorAll('button')]"
         ".find(b=>b.textContent.trim()==='뒤에서')?.click()},300))</script></body>")
i = src.rfind('</body>')
tmp = tempfile.mkdtemp(prefix='school_maps_')
page = os.path.join(tmp, 'sim.html')
open(page, 'w', encoding='utf-8').write(src[:i] + click + src[i + 7:])
os.makedirs(OUT, exist_ok=True)
for k in ids:
    png = os.path.join(tmp, k + '.png')
    subprocess.run([CHROME, '--headless=new', '--use-angle=d3d11', '--enable-unsafe-swiftshader', '--hide-scrollbars',
                    '--window-size=1280,720', '--virtual-time-budget=10000', '--user-data-dir=' + os.path.join(tmp, 'prof'),
                    '--screenshot=' + png, 'file:///' + page.replace('\\', '/') + '#' + k],
                   capture_output=True, timeout=120)
    Image.open(png).convert('RGB').crop((16, 16, 974, 656)).save(os.path.join(OUT, k + '.jpg'), quality=80, optimize=True)
    print('찍음', k)
print('ok', len(ids), '장 →', OUT)
