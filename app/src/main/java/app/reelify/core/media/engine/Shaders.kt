package app.reelify.core.media.engine

/**
 * GLSL ES 1.00 shader sources. The beauty/color fragment body is generated for both a regular
 * sampler2D (Media3 export/preview pipeline) and samplerExternalOES (live camera), so the camera
 * preview and the rendered reel use the exact same math.
 */
object Shaders {
    val VERTEX = """
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTex;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 t = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTex = (uTexTransformationMatrix * t).xy;
}
""".trimIndent()

    fun beautyFragment(external: Boolean): String {
        val header = if (external) "#extension GL_OES_EGL_image_external : require\nprecision highp float;\nuniform samplerExternalOES uTexSampler;\n"
        else "precision highp float;\nuniform sampler2D uTexSampler;\n"
        return header + """
varying vec2 vTex;
uniform vec2 uTexel;
uniform float uScale;
uniform float uAspect;
uniform vec2 uUvMin;
uniform vec2 uUvMax;
uniform float uSmooth;
uniform float uTone;
uniform float uUnderEye;
uniform float uRefine;
uniform float uExposure;
uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uWarmth;
uniform float uTint;
uniform float uHighlights;
uniform float uShadows;
uniform float uClarity;
uniform float uSharpen;
uniform float uSplit;
uniform vec4 uFace;
uniform vec4 uEyes;
uniform float uFaceOn;

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
vec3 tex(vec2 p) { return texture2D(uTexSampler, clamp(p, uUvMin, uUvMax)).rgb; }
// Aspect-corrected space so ellipses are round in pixels.
vec2 A(vec2 p) { return vec2(p.x * uAspect, p.y); }
float ellipse(vec2 p, vec2 c, vec2 r) {
  vec2 d = (A(p) - A(c)) / max(r, vec2(0.0001));
  return 1.0 - smoothstep(0.75, 1.15, length(d));
}
float skinMask(vec3 c) {
  float y = luma(c);
  float cb = 0.5 - 0.168736 * c.r - 0.331264 * c.g + 0.5 * c.b;
  float cr = 0.5 + 0.5 * c.r - 0.418688 * c.g - 0.081312 * c.b;
  float m = smoothstep(0.29, 0.34, cb) * (1.0 - smoothstep(0.50, 0.55, cb));
  m *= smoothstep(0.505, 0.54, cr) * (1.0 - smoothstep(0.67, 0.72, cr));
  return m * smoothstep(0.06, 0.18, y) * (1.0 - smoothstep(0.96, 1.0, y));
}

void main() {
  vec2 uv = vTex;
  if (uFaceOn > 0.5 && uRefine > 0.001) {
    vec2 d = (A(uv) - A(uFace.xy)) / max(uFace.zw, vec2(0.0001));
    float lower = smoothstep(-0.05, 0.55, -d.y);
    float fall = 1.0 - smoothstep(0.55, 1.1, length(d));
    uv.x += (uv.x - uFace.x) * uRefine * 0.05 * lower * fall;
  }
  vec3 c0 = tex(uv);
  vec3 col = c0;
  float faceM = 1.0;
  if (uFaceOn > 0.5) faceM = mix(0.35, 1.0, ellipse(uv, uFace.xy, uFace.zw * 1.3));
  float sk = skinMask(c0) * faceM;
  vec3 blurL = c0;
  if (uSmooth > 0.001 || uTone > 0.001 || uClarity > 0.001) {
    vec2 rad = uTexel * (2.0 + 4.5 * uSmooth) * uScale;
    vec3 sum = c0; float wsum = 1.0; vec3 acc = vec3(0.0);
    float l0 = luma(c0);
    for (int ring = 1; ring <= 2; ring++) {
      for (int k = 0; k < 8; k++) {
        float a = float(k) * 0.785398 + float(ring) * 0.392699;
        vec2 o = vec2(cos(a), sin(a)) * rad * float(ring);
        vec3 s = tex(uv + o);
        float dl = luma(s) - l0;
        float w = exp(-dl * dl * 220.0);
        sum += s * w; wsum += w; acc += s;
      }
    }
    vec3 bil = sum / wsum;
    blurL = acc / 16.0;
    vec3 detail = c0 - blurL;
    vec3 smoothed = bil + detail * 0.3;
    col = mix(c0, smoothed, clamp(uSmooth * sk * 1.15, 0.0, 0.9));
    float y = luma(col);
    vec3 chroma = col - vec3(y);
    vec3 bchroma = bil - vec3(luma(bil));
    col = vec3(y) + mix(chroma, bchroma * 0.94, clamp(uTone * sk, 0.0, 1.0));
  }
  if (uFaceOn > 0.5 && uUnderEye > 0.001 && uEyes.x >= 0.0) {
    float eyeDist = distance(A(uEyes.xy), A(uEyes.zw));
    vec2 r = vec2(eyeDist * 0.34, eyeDist * 0.16);
    vec2 off = vec2(0.0, -eyeDist * 0.24);
    float m = max(ellipse(uv, uEyes.xy + vec2(off.x / uAspect, off.y), r), ellipse(uv, uEyes.zw + vec2(off.x / uAspect, off.y), r));
    float y = luma(col);
    col += vec3(0.07) * (1.0 - y) * uUnderEye * m;
  }
  if (uSharpen > 0.001) {
    vec2 t = uTexel * uScale;
    vec3 n = tex(uv + vec2(0.0, t.y)) + tex(uv - vec2(0.0, t.y)) + tex(uv + vec2(t.x, 0.0)) + tex(uv - vec2(t.x, 0.0));
    float lap = luma(c0) * 4.0 - luma(n);
    col += vec3(lap) * uSharpen * 0.5 * (1.0 - sk * 0.6);
  }
  if (uClarity > 0.001) {
    float y = luma(col);
    float mid = 1.0 - abs(y - 0.5) * 2.0;
    col += vec3(y - luma(blurL)) * uClarity * 0.9 * mid * (1.0 - sk * 0.5);
  }
  // ----- grade -----
  col *= exp2(uExposure);
  col += vec3(uBrightness * 0.22);
  col = (col - 0.5) * (1.0 + uContrast * 0.55) + 0.5;
  float y = luma(col);
  col -= col * smoothstep(0.55, 1.0, y) * uHighlights * 0.22;
  col += vec3(1.0 - smoothstep(0.0, 0.45, y)) * uShadows * 0.16;
  y = luma(col);
  col += uSplit * mix(vec3(-0.02, 0.008, 0.03), vec3(0.03, 0.01, -0.022), smoothstep(0.2, 0.8, y));
  y = luma(col);
  col = mix(vec3(y), col, 1.0 + uSaturation);
  col.r += uWarmth * 0.055; col.b -= uWarmth * 0.055; col.g += uTint * 0.035;
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }

    /** Composition-level finishing: freeze-frame transitions, flash, blur, zoom, vignette, light overlay. */
    val FINISH_FRAGMENT = """
precision highp float;
varying vec2 vTex;
uniform sampler2D uTexSampler;
uniform sampler2D uSnap;
uniform vec2 uTexel;
uniform float uScale;
uniform float uMode;      // 0 none, 1 crossfade, 2 zoom, 3 dissolve, 4 slide, 5 flash, 6 blur
uniform float uProgress;  // 0..1 within the transition window
uniform float uHasSnap;
uniform float uVignette;
uniform float uLight;
uniform float uTime;
uniform float uAspect;

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
vec3 blur(sampler2D s, vec2 uv, float r) {
  vec3 acc = texture2D(s, uv).rgb; float n = 1.0;
  for (int k = 0; k < 12; k++) {
    float a = float(k) * 0.5236;
    vec2 o = vec2(cos(a), sin(a)) * uTexel * r * uScale;
    acc += texture2D(s, clamp(uv + o, 0.0, 1.0)).rgb + texture2D(s, clamp(uv + o * 0.5, 0.0, 1.0)).rgb;
    n += 2.0;
  }
  return acc / n;
}
float ease(float x) { return x * x * (3.0 - 2.0 * x); }

void main() {
  vec2 uv = vTex;
  float p = clamp(uProgress, 0.0, 1.0);
  vec3 col;
  if (uMode > 1.5 && uMode < 2.5) {
    float z = 1.0 + 0.07 * (1.0 - ease(p));
    vec2 zuv = (uv - 0.5) / z + 0.5;
    col = texture2D(uTexSampler, zuv).rgb;
    if (uHasSnap > 0.5) col = mix(texture2D(uSnap, uv).rgb, col, ease(min(1.0, p * 1.6)));
  } else if (uMode > 3.5 && uMode < 4.5) {
    float e = ease(p);
    if (uHasSnap > 0.5) {
      float edge = 1.0 - e;
      if (uv.x < edge) col = texture2D(uSnap, vec2(uv.x + e, uv.y)).rgb * (1.0 - 0.25 * e);
      else col = texture2D(uTexSampler, vec2(uv.x - edge, uv.y)).rgb;
    } else col = texture2D(uTexSampler, uv).rgb;
  } else if (uMode > 5.5) {
    float r = 22.0 * (1.0 - abs(p * 2.0 - 1.0));
    col = r > 0.5 ? blur(uTexSampler, uv, r) : texture2D(uTexSampler, uv).rgb;
  } else {
    col = texture2D(uTexSampler, uv).rgb;
    if (uHasSnap > 0.5 && uMode > 0.5 && uMode < 1.5) col = mix(texture2D(uSnap, uv).rgb, col, ease(p));
    if (uHasSnap > 0.5 && uMode > 2.5 && uMode < 3.5) {
      float n = hash(floor(uv / (uTexel * 3.0 * uScale)));
      float t = smoothstep(n - 0.15, n + 0.15, p * 1.3 - 0.15);
      col = mix(texture2D(uSnap, uv).rgb, col, t);
    }
  }
  if (uMode > 4.5 && uMode < 5.5) {
    float f = 1.0 - abs(p * 2.0 - 1.0);
    col = mix(col, vec3(1.0, 0.97, 0.94), ease(f) * 0.85);
  }
  if (uLight > 0.001) {
    vec2 c = vec2(0.15 + 0.7 * fract(uTime * 0.035), 0.85);
    vec2 d = vec2((uv.x - c.x) * uAspect, uv.y - c.y);
    float g = exp(-dot(d, d) * 3.5);
    col = col + vec3(1.0, 0.72, 0.55) * g * 0.13 * uLight * (1.0 - col);
  }
  if (uVignette > 0.001) {
    vec2 d = vec2((uv.x - 0.5) * uAspect, uv.y - 0.5);
    float v = smoothstep(0.85, 0.25, length(d) * 1.25);
    col *= mix(1.0, v, uVignette * 0.7);
  }
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
""".trimIndent()

    val COPY_FRAGMENT = """
precision mediump float;
varying vec2 vTex;
uniform sampler2D uTexSampler;
void main() { gl_FragColor = texture2D(uTexSampler, vTex); }
""".trimIndent()
}
