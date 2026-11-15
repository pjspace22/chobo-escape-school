# 초보 탈출 학교: 기획팀 웹 원본 HTML 두 개를 앱 assets/school_web/ 으로 옮기며 앱용으로 고친다
#
# 개발 방식(plan-school-reset-basics.md §9, 현규 결정 11/10): 웹(HTML)으로 먼저 만들고 PC 브라우저에서 확인 → 이 스크립트로 앱에 옮김
# 원본: origin/main 의 materials/documents/
#   plan-school-parking-sim-3d.html      → sim.html   (0.19.0부터 연습 맵 사진 화면, 사진은 shoot_maps.py)
#   plan-school-drive-cards-prototype.html → cards.html (홈 + 운전 카드)
# 쓰는 법 (저장소 맨 위에서):  git fetch origin && python tools/school_web/mk_assets.py
#   다른 커밋의 원본을 쓰려면:  python tools/school_web/mk_assets.py <커밋>
#
# 여기서는 "앱 전용" 수정만 한다 (Android 다리·화면 폭 1280·로컬 three.js·앱 기록 칸).
# 로고 지운 모델·물리 나눠 계산·픽셀 비율 1 등 공통 수정은 웹 원본에 이미 들어 있다 (main df6ea37).
# 원본 문구가 바뀌어 고칠 곳을 못 찾으면 멈추고 그 문구를 알려 준다 → 이 스크립트의 해당 줄을 고친다.
# three.min.js·GLTFLoader.js·LICENSE-three.txt(three.js r128, MIT)는 assets에 이미 있어 건드리지 않는다.
import json, os, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'school_web')
REF = sys.argv[1] if len(sys.argv) > 1 else 'origin/main'

def original(name):
    return subprocess.run(['git', 'show', f'{REF}:materials/documents/{name}'], cwd=ROOT,
                          capture_output=True, check=True).stdout.decode('utf-8').replace('\r\n', '\n')

def rep(s, a, b, count=1):
    n = s.count(a)
    if n == 0 or (count == 1 and n != 1):
        sys.exit(f'못 찾음/여러 개({n}): {a[:80]}')
    return s.replace(a, b)

# 0.19.0: 앱 안 애니메이션 끔 (현규 결정 11/10, 에뮬레이터 렉)
NO_ANIM_CSS = '*{animation:none!important;transition:none!important}\n'
APP_VIEWPORT = '<meta name="viewport" content="width=1280, user-scalable=no">'
FULL_CSS = ('\n/* 앱(WebView): 차 화면 전체를 쓴다. 판단: 원본 16:9 틀(1280px)을 화면 폭에 맞춰 키움 */\n'
            'body{padding:0;background:var(--bg);height:100vh;overflow:hidden}\n'
            '#car{max-width:none;aspect-ratio:auto;height:100vh;border-radius:0}\n')

# 사진 화면 (앱 sim.html). 웹 3D와 같은 색·글꼴 크기, 왼쪽 사진 + 오른쪽 설명
PHOTO_PAGE = '''<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=1280, user-scalable=no">
<title>연습 맵</title>
<!-- 앱용: 웹 3D 연습(plan-school-parking-sim-3d.html)의 맵을 사진으로 보여 준다. tools/school_web/mk_assets.py가 만듦 -->
<style>
:root{--bg:#EEF1F5;--sf:#fff;--tx:#141A22;--sub:#4A5564;--pr:#2E6BF6;--prs:#E8EFFE;--f:"Malgun Gothic",sans-serif}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);font-family:var(--f);color:var(--tx);height:100vh;overflow:hidden}
#car{height:100vh;display:grid;grid-template-columns:1fr 360px}
#view{position:relative;min-width:0;background:#D3D8DF}
#view img{display:block;width:100%;height:100%;object-fit:cover}
.side{background:var(--sf);padding:22px 24px;display:flex;flex-direction:column;gap:14px;min-width:0;overflow-y:auto}
.back{font-size:17px;font-weight:600;color:var(--pr);text-decoration:none}
.eb{font-size:14px;font-weight:600;letter-spacing:1.2px;color:var(--pr)}
h1{font-size:28px;margin:0}
.goal{font-size:18px;color:var(--tx);line-height:1.55;margin:0}
.note{font-size:15px;color:var(--sub);line-height:1.5;margin:0;background:var(--bg);border-radius:12px;padding:12px 14px}
.row{display:flex;gap:10px;margin-top:auto}
.btn{flex:1;border:0;border-radius:14px;padding:14px 10px;font-size:17px;font-weight:600;font-family:inherit;background:var(--bg);color:var(--tx)}
.btn:disabled{opacity:.35}
.btn:focus-visible{outline:3px solid var(--pr);outline-offset:2px}
</style>
</head>
<body>
<div id="car">
  <div id="view"><img id="ph" alt=""></div>
  <div class="side">
    <a class="back" href="cards.html" onclick="if(window.Android){Android.home();return false}">← 홈</a>
    <span class="eb" id="eb"></span>
    <h1 id="ti"></h1>
    <p class="goal" id="goal"></p>
    <p class="note">사진으로 보는 연습 맵이에요. 실제 차에서 운전할 때 이 장면을 떠올려 보세요.</p>
    <div class="row"><button class="btn" id="prev">◀ 이전 맵</button><button class="btn" id="next">다음 맵 ▶</button></div>
  </div>
</div>
<script>
/*MAPS*/
const IDS=Object.keys(MAPS);
function show(){
  const id=MAPS[location.hash.slice(1)]?location.hash.slice(1):IDS[0],m=MAPS[id],i=IDS.indexOf(id);
  document.title=m.title;
  ph.src='maps/'+id+'.jpg';ph.alt=m.title+' 연습 맵 사진';
  eb.textContent='연습 맵 · '+TIER_NAME[m.t];ti.textContent=m.title;goal.textContent=m.goal;
  prev.disabled=i===0;next.disabled=i===IDS.length-1;
  prev.onclick=()=>{location.hash=IDS[i-1]};next.onclick=()=>{location.hash=IDS[i+1]};
}
addEventListener('hashchange',show);show();
</script>
</body>
</html>
'''

# ── 3D 운전 연습 → 앱은 사진 ──
# 0.19.0 현규 결정(11/10): 에뮬레이터 렉 때문에 앱 안의 3D 맵은 사진으로 (움직이는 연습은 웹 버전에서 개발).
# PHOTOS=True: 웹 원본의 맵 정의(MAPS·TIER_NAME)만 꺼내 사진 화면 sim.html을 만든다. 사진은 shoot_maps.py가 maps/에 찍음.
# PHOTOS=False: 예전처럼 웹 3D를 그대로 옮김 (three.min.js·GLTFLoader.js 필요)
PHOTOS = True
s = original('plan-school-parking-sim-3d.html')
if PHOTOS:
    maps_js = s[s.index('const MAPS={'):s.index('const TIER_NAME')]
    tier_js = s[s.index('const TIER_NAME'):].split('\n', 1)[0]
    page = PHOTO_PAGE.replace('/*MAPS*/', maps_js + tier_js)
    open(os.path.join(OUT, 'sim.html'), 'w', encoding='utf-8', newline='\n').write(page)
else:
    s = original('plan-school-parking-sim-3d.html')
    s = rep(s, '<meta name="viewport" content="width=device-width, initial-scale=1">', APP_VIEWPORT)
    s = rep(s, '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;600;700&display=swap">\n', '')
    s = rep(s, '</style>', FULL_CSS + '/* 차에는 키보드가 없어 키보드 안내는 숨김 */\n.keys:not(#credit){display:none}\n</style>')
    s = rep(s, '<a href="plan-school-drive-cards-prototype.html"', '<a href="cards.html" onclick="if(window.Android){Android.home();return false}"')
    s = rep(s, '<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>', '<script src="three.min.js"></script>')
    s = rep(s, '<script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/loaders/GLTFLoader.js"></script>', '<script src="GLTFLoader.js"></script>')
    s = rep(s, '3D 라이브러리를 불러오지 못했어요.<br>인터넷 연결을 확인해 주세요.', '3D 화면을 열지 못했어요.<br>앱을 다시 켜 주세요.')
    open(os.path.join(OUT, 'sim.html'), 'w', encoding='utf-8', newline='\n').write(s)

# ── 운전 카드 (홈 + 카드) ──
c = original('plan-school-drive-cards-prototype.html')
c = rep(c, '<meta name="viewport" content="width=device-width, initial-scale=1">', APP_VIEWPORT)
c = rep(c, '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@400;500;600;700&display=swap">\n', '')
c = rep(c, '</style>', FULL_CSS + NO_ANIM_CSS + '</style>')
# 0.19.0: 앱은 3D 대신 사진 (현규 결정 11/10)
c = rep(c, '<b>3D 운전 연습</b><small>운전 카드마다 연습 맵이 있어요</small>', '<b>연습 맵 사진</b><small>운전 카드마다 연습 장면이 있어요</small>')
c = rep(c, '3D로 연습하기', '연습 맵 사진 보기')
# 상태: 앱이면 앱 저장소(SharedPreferences)에 — 주행 판정·안내 켜고 끄기와 같이 쓰려고
c = rep(c, "try{S=JSON.parse(localStorage.getItem('dc_state')||'{}')}catch(e){S={}}",
        "const loadS=()=>{try{return JSON.parse((window.Android?Android.loadState():localStorage.getItem('dc_state'))||'{}')}catch(e){return {}}};\nS=loadS();")
c = rep(c, "const save=()=>{try{localStorage.setItem('dc_state',JSON.stringify(S))}catch(e){}};",
        "const save=()=>{try{if(window.Android)Android.saveState(JSON.stringify(S));else localStorage.setItem('dc_state',JSON.stringify(S))}catch(e){}};\n"
        "// 앱이 주는 실제 값 (방금 주행·내 기록·시연 모드). 앱 밖에서는 예시 그대로\n"
        "let APP=null;const loadApp=()=>{try{APP=window.Android?JSON.parse(Android.appData()):null}catch(e){APP=null}};loadApp();\n"
        "function nav(k){if(window.Android)Android.open(k);else go({report:'ph1',record:'ph2',settings:'settings'}[k])}\n"
        "// 앱에서 다른 화면을 갔다 오면 다시 읽어 그림\n"
        "window.refreshApp=()=>{S=loadS();CARDS.forEach(c=>{if(!S[c.id])S[c.id]={streak:0,m:null,coach:c.t===1}});loadApp();if(view!=='cards'&&view!=='home')view='home';render();showNewMaster()};")
c = rep(c, "onclick=\"go('settings')\" aria-label=\"설정\"", "onclick=\"nav('settings')\" aria-label=\"설정\"")
# 방금 주행·내 기록 칸: 앱의 실제 기록
c = rep(c, """      <button class="panel" onclick="go('ph1')"><div class="ph-h"><span class="eb">방금 주행</span><span class="ex">예시</span></div>
        <div class="big"><span><b>8.2</b><small>km</small></span><span><b>23</b><small>분</small></span></div>
        <div class="pairs"><span class="pr">꺾기 전 깜빡이 5 / 5</span><span class="pr n">켜 둔 채 1번</span></div></button>""",
        """      <button class="panel" onclick="nav('report')">${lastPanel()}</button>""")
c = rep(c, """      <button class="panel" onclick="go('ph2')"><div class="ph-h"><span class="eb">내 기록</span><span class="ex">예시</span></div>
        <span style="font-size:17px;color:var(--sub)">꺾기 전 깜빡이 · 최근 10번</span>${spark()}</button>""",
        """      <button class="panel" onclick="nav('record')">${histPanel()}</button>""")
c = rep(c, "onclick=\"location.href='plan-school-parking-sim-3d.html'\"", "onclick=\"location.href='sim.html'\"")
c = rep(c, "onclick=\"location.href='plan-school-parking-sim-3d.html#${c.id}'\"", "onclick=\"location.href='sim.html#${c.id}'\"")
# 시연 버튼: 앱에서는 시연 모드일 때만
c = rep(c, "${judge?`<div class=\"demo\">", "${judge&&(!APP||APP.demo)?`<div class=\"demo\">")
c = rep(c, "function spark(){const v=[40,55,50,70,65,80,75,90,100,100],w=300,h=70,bw=w/v.length;\n return `<svg class=\"spark\" viewBox=\"0 0 ${w} ${h}\" preserveAspectRatio=\"none\" role=\"img\" aria-label=\"최근 10번 깜빡이 비율, 40%에서 100%로 오름\">",
        "function spark(vals){const v=vals||[40,55,50,70,65,80,75,90,100,100],w=300,h=70,bw=w/Math.max(v.length,1);\n return `<svg class=\"spark\" viewBox=\"0 0 ${w} ${h}\" preserveAspectRatio=\"none\" role=\"img\" aria-label=\"최근 ${v.length}번 깜빡이 비율\">")
c = rep(c, "function pass(c){", """function lastPanel(){const L=APP&&APP.last;
  if(!L)return `<div class="ph-h"><span class="eb">방금 주행</span><span class="ex">예시</span></div><div class="big"><span><b>8.2</b><small>km</small></span><span><b>23</b><small>분</small></span></div><div class="pairs"><span class="pr">꺾기 전 깜빡이 5 / 5</span><span class="pr n">켜 둔 채 1번</span></div>`;
  return `<div class="ph-h"><span class="eb">방금 주행</span><span class="ex" style="background:var(--prs);color:var(--pr)">${L.when}</span></div><div class="big"><span><b>${L.km}</b><small>${L.unit||'km'}</small></span><span><b>${L.score}</b><small>점</small></span></div><div class="pairs"><span class="pr">꺾기 전 깜빡이 ${L.sig} / ${L.turns}</span><span class="pr ${L.leftOn?'n':''}">켜 둔 채 ${L.leftOn}번</span></div>`}
function histPanel(){const H=APP&&APP.hist;
  if(!H||!H.length)return `<div class="ph-h"><span class="eb">내 기록</span><span class="ex">예시</span></div><span style="font-size:17px;color:var(--sub)">꺾기 전 깜빡이 · 최근 10번</span>${spark()}`;
  return `<div class="ph-h"><span class="eb">내 기록</span><span class="ex" style="background:var(--prs);color:var(--pr)">주행 ${APP.trips}번</span></div><span style="font-size:17px;color:var(--sub)">꺾기 전 깜빡이 · 최근 ${H.length}번</span>${spark(H)}`}
// 주행 기록으로 새로 마스터한 카드 (앱이 채움): 홈에 오면 한 장씩 보여 줌
function showNewMaster(){const ids=(APP&&APP.newMaster)||[];if(!ids.length||popup)return;const c=CARDS.find(x=>x.id===ids[0]);if(!c)return;
  APP.newMaster=ids.slice(1);if(window.Android)Android.ackMaster(c.id);
  popup=masterCard(c,`실수 없이 ${NEED}번 연속 해냈어요.<br>이제 주행 중 안내는 꺼 둘게요. 언제든 다시 켤 수 있어요.`,'마스터 카드');render()}
function pass(c){""")
c = rep(c, "function closePop(){popup=window._next||null;window._next=null;render()}",
        "function closePop(){popup=window._next||null;window._next=null;render();if(!popup)showNewMaster()}")
c = rep(c, "\nrender();\n</script>", "\nrender();showNewMaster();\n</script>")
# 0.19.0: 카드 그림(SVG 애니메이션)을 멈춘 한 장면으로 (현규 11/10 "운전카드들도 멈춰있는 사진으로").
# 카드마다 그 장면이 잘 보이는 순간 = 가장 긴 움직임 길이의 비율. PC 브라우저에서 0.2·0.4·0.6·0.8을 비교해 고름
FREEZE = {'signal_on': 0.4, 'signal_off': 0.6, 'park_p': 0.8, 'headlight': 0.4, 'rain': 0.4, 'right_red': 0.6,
          'left_wait': 0.4, 'one_lane': 0.2, 'soft_stop': 0.6, 'bump': 0.2, 'rain_light': 0.4, 'high_beam': 0.4,
          'interior': 0.4, 'fog': 0.4, 'right_lane': 0.4, 'exit_lane': 0.4, 'merge': 0.6, 'hw_stop': 0.4,
          'early_slow': 0.4, 'wobble': 0.4, 'cruise': 0.6, 'seat_heat': 0.4, 'manners': 0.4}
c = rep(c, "const sceneSVG=(id,cls='scene')=>`<svg class=\"${cls}\" ", "const sceneSVG=(id,cls='scene')=>`<svg data-scene=\"${id}\" class=\"${cls}\" ")
c = rep(c, "\nrender();showNewMaster();\n</script>",
        "\n// 앱: 그림을 멈춘 한 장면으로 (애니메이션 끔). 카드 그림은 FREEZE 비율, 나머지 그림은 0.3초 지점\n"
        "const FREEZE=" + json.dumps(FREEZE) + ";\n"
        "function freezeScenes(){document.querySelectorAll('svg').forEach(s=>{if(!s.pauseAnimations)return;const id=s.dataset.scene;"
        "let t=0.3;if(id&&FREEZE[id]!=null){const d=Math.max(0,...[...s.querySelectorAll('animate,animateMotion,animateTransform')].map(a=>parseFloat(a.getAttribute('dur'))||0));t=d*FREEZE[id]}"
        "s.pauseAnimations();s.setCurrentTime(t)})}\n"
        "const _render=render;render=function(){_render();freezeScenes()};\n"
        "render();showNewMaster();\n</script>")
open(os.path.join(OUT, 'cards.html'), 'w', encoding='utf-8', newline='\n').write(c)
print('ok', sorted(os.listdir(OUT)))
