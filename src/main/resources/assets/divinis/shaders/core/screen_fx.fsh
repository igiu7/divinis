#version 150

// Divinis screen effects: one pass over a copy of the finished frame (client/ScreenFxClient)

uniform sampler2D Sampler0;   // the finished frame
uniform sampler2D Sampler1;   // a thing's shape, white (MaskMode 2)
uniform vec2 OutSize;
uniform float Time;
uniform float Invert;
uniform float Blur;
uniform float Aberration;
uniform float Distort;
uniform float Desaturate;
uniform vec4 KeepColor;   // rgb: a colour left in colour when desaturated, a: 1 if there is one
uniform float Glitch;
uniform float Noise;
uniform float Pixelate;
uniform float MaskMode;       // 0 the whole screen, 1 a circle, 2 a thing's shape
uniform float MaskInvert;     // 1: everywhere but there
uniform vec4 Circle;          // middle (0..1), radius and its soft edge (pixels)
uniform vec4 OutlineColor;    // rgb, a: how much
uniform float OutlineWidth;   // pixels
uniform vec4 Flash;           // rgb, a: how much light on that part
uniform float AuraBlur;       // a blur round a thing's shape: how strong
uniform float AuraWidth;      // and how far out (pixels)
uniform float Heat;           // the air shimmering over and round a thing's shape: how much
uniform float HeatWidth;      // and how far out (pixels)
uniform vec4 Lens;            // a black hole: middle (0..1), its radius (pixels), how much space bends round it
uniform sampler2D Sampler2;   // the world's depth, copied after the world was drawn (Kaleido)
uniform sampler2D Sampler3;   // the world's picture copied with it (before the hand and the held item were drawn)
uniform vec4 Shock;           // a shock wave: middle (0..1), how far it has run (pixels), how strong
uniform float ShockWidth;     // how wide its crest is (pixels)
uniform vec3 Radial;          // a radial blur: middle (0..1), how strong
uniform float Kaleido;        // the sky (where nothing was drawn) a grey kaleidoscope of the world below it
uniform float KaleidoColor;   // 0: the kaleidoscope grey, 1: in colour
uniform float Timestop;       // time stopped: colour drained, a cold tint, a still grain, the edges dark
uniform vec2 Shatter;         // the picture broken like glass: how much, and which breaking (a seed)
uniform vec4 Glow;            // a soft glow over everything bright: its colour, how strong
uniform float Rewind;         // time running backwards: sepia, a running grain, the colours split the other way
uniform float EdgeAberration; // (with Kaleido) the colours split towards the edges of the screen, always
// the true form's pass (the world's places found from its depth: camera-relative)
uniform float CreatorPass;    // 1: this pass
uniform float Exposure;       // looked at too long: the picture overexposed, its bright parts burnt out
uniform float Bleach;         // the world's colour draining away
uniform vec4 BleachAt;        // where the god is (camera-relative) and how far round it the world is flat white
uniform vec4 RestoreAt;       // where the colour has come back (camera-relative) and how far (w < 0: nowhere)
uniform vec2 Reveal;          // the world being made: x 1, y the height made so far (camera-relative)
uniform mat4 InvViewProj;     // from the screen back into the world (camera-relative)

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec2 hash2(vec2 p) {
    return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453);
}

vec3 blurAt(vec2 uv, float radius) {
    if (radius < 0.15) return texture(Sampler0, uv).rgb;
    // a golden-angle spiral of taps, as far out as the blur reaches
    vec2 px = 1.0 / OutSize;
    vec3 sum = texture(Sampler0, uv).rgb;
    float n = 1.0;
    for (int i = 1; i < 24; i++) {
        float r = sqrt(float(i) / 24.0) * radius;
        float a = float(i) * 2.39996;
        sum += texture(Sampler0, uv + vec2(cos(a), sin(a)) * r * px).rgb;
        n += 1.0;
    }
    return sum / n;
}

vec3 blurred(vec2 uv) {
    return blurAt(uv, Blur * 14.0);
}

vec3 hue(vec3 c) {
    float mx = max(c.r, max(c.g, c.b)), mn = min(c.r, min(c.g, c.b));
    float d = mx - mn;
    float h = 0.0;
    if (d > 1e-4) {
        if (mx == c.r) h = mod((c.g - c.b) / d, 6.0);
        else if (mx == c.g) h = (c.b - c.r) / d + 2.0;
        else h = (c.r - c.g) / d + 4.0;
    }
    return vec3(h / 6.0, mx < 1e-4 ? 0.0 : d / mx, mx);
}

void main() {
    vec2 uv = texCoord;

    // passes of their own (client/ScreenFxClient: shock, radial, kaleido, the true form's)
    if (CreatorPass > 0.5) {
        vec3 c = texture(Sampler0, texCoord).rgb;
        float depth = texture(Sampler2, texCoord).r;
        // the hand and what it holds were drawn after the world: where the picture differs from the world's, keep it
        bool hand = length(c - texture(Sampler3, texCoord).rgb) > 0.03;
        bool sky = depth >= 0.99999;
        vec4 wp = InvViewProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec3 rel = wp.xyz / wp.w;
        if (Bleach > 0.001 && !sky && !hand) {
            float keep = RestoreAt.w >= 0.0 && length(rel - RestoreAt.xyz) < RestoreAt.w ? 1.0 : 0.0;
            float g = dot(c, vec3(0.299, 0.587, 0.114));
            float near = 1.0 - smoothstep(BleachAt.w * 0.6, BleachAt.w, length(rel - BleachAt.xyz));
            vec3 b = mix(c, vec3(g), Bleach);
            b = mix(b, vec3(0.95), near * 0.85);
            c = mix(b, c, keep);
        }
        if (Exposure > 0.001) {
            c = vec3(1.0) - exp(-c * (1.0 + Exposure * 5.0));
            float lum = dot(c, vec3(0.299, 0.587, 0.114));
            c = mix(c, vec3(1.0), smoothstep(0.55, 0.95, lum) * Exposure);
            c += max(blurAt(texCoord, 10.0 * Exposure) - vec3(0.5), vec3(0.0)) * Exposure;
        }
        if (Reveal.x > 0.5 && !hand) {
            float made = Reveal.y - rel.y;
            if (sky || made < 0.0) c = vec3(0.0);
            else c += vec3(0.6, 0.9, 1.0) * (1.0 - smoothstep(0.0, 0.7, made)) * 0.9;
        }
        fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
        return;
    }
    if (Kaleido > 0.001) {
        vec3 base = texture(Sampler0, texCoord).rgb;
        if (EdgeAberration > 0.001) {
            vec2 eo = (texCoord - 0.5) * dot(texCoord - 0.5, texCoord - 0.5) * EdgeAberration * 0.06;
            base = vec3(texture(Sampler0, texCoord + eo).r, base.g, texture(Sampler0, texCoord - eo).b);
        }
        bool hand = length(texture(Sampler0, texCoord).rgb - texture(Sampler3, texCoord).rgb) > 0.03;
        if (texture(Sampler2, texCoord).r < 0.99999 || hand) {
            fragColor = vec4(base, 1.0);
            return;
        }
        // the sky folded into eight mirrored wedges round a point over the horizon, each a mirror of the world below
        float aspect = OutSize.x / OutSize.y;
        vec2 p = (texCoord - vec2(0.5, 0.58)) * vec2(aspect, 1.0);
        float seg = 6.2831853 / 8.0;
        float a = mod(atan(p.y, p.x) + Time * 0.04, seg);
        a = abs(a - seg * 0.5);
        float r = length(p);
        vec2 q = vec2(cos(a), sin(a)) * r;
        vec2 src = vec2(0.5 + (q.x * 0.8) / aspect, 0.06 + abs(fract(q.y * 1.6 + r * 0.7) - 0.5) * 0.7);
        vec3 c = texture(Sampler3, src).rgb; // mirrored from the world alone, not the hand
        float g = dot(c, vec3(0.299, 0.587, 0.114));
        vec3 mono = mix(vec3(g) * vec3(1.0, 0.98, 1.06) + 0.04, c * 1.15 + 0.03, clamp(KaleidoColor, 0.0, 1.0));
        mono = mix(mono, vec3(1.0), clamp(KaleidoColor - 1.0, 0.0, 1.0)); // (2: white)
        fragColor = vec4(mix(base, mono, clamp(Kaleido, 0.0, 1.0)), 1.0);
        return;
    }
    if (Shock.w > 0.001) {
        vec2 dv = (uv - Shock.xy) * OutSize;
        float d = length(dv);
        float x = (d - Shock.z) / max(1.0, ShockWidth);
        vec3 c;
        if (abs(x) < 1.0 && d > 0.5) {
            float crest = cos(x * 1.5708);
            float push = sin(x * 3.14159) * crest * Shock.w * ShockWidth * 0.35;
            c = texture(Sampler0, uv - dv / d * push / OutSize).rgb + vec3(crest * crest * 0.12 * Shock.w);
        } else {
            c = texture(Sampler0, uv).rgb;
        }
        fragColor = vec4(c, 1.0);
        return;
    }
    if (Radial.z > 0.001) {
        vec3 sum = vec3(0.0);
        for (int i = 0; i < 16; i++) {
            sum += texture(Sampler0, mix(uv, Radial.xy, float(i) / 15.0 * Radial.z * 0.25)).rgb;
        }
        fragColor = vec4(sum / 16.0, 1.0);
        return;
    }

    // space bends round a black hole: what is round its edge is pulled in (its black core stays black)
    if (Lens.w > 0.001) {
        vec2 dv = (uv - Lens.xy) * OutSize;
        float d = length(dv);
        float rE = Lens.z;
        if (d > rE * 0.98) {
            float f = 1.0 - smoothstep(rE * 1.5, rE * 4.5, d);
            float nd = max(0.0, d - f * Lens.w * rE * rE / d);
            uv = Lens.xy + dv * (nd / d) / OutSize;
        }
    }

    // heat: the air shimmers in and round the shape, most of all just over it
    float zone = 0.0;
    if (MaskMode > 1.5 && Heat > 0.001) {
        vec2 px = 1.0 / OutSize;
        float near = texture(Sampler1, texCoord).r;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7854;
            vec2 dir = vec2(cos(a), sin(a)) * px;
            near = max(near, texture(Sampler1, texCoord + dir * HeatWidth * 0.5).r * 0.8);
            near = max(near, texture(Sampler1, texCoord + dir * HeatWidth).r * 0.45);
        }
        near = max(near, texture(Sampler1, texCoord - vec2(0.0, HeatWidth * 1.4) * px).r * 0.75);
        zone = clamp(near, 0.0, 1.0);
        vec2 wobble = vec2(sin(texCoord.y * OutSize.y * 0.07 + Time * 9.0) + sin(texCoord.y * OutSize.y * 0.031 - Time * 5.3),
                           cos(texCoord.x * OutSize.x * 0.05 + Time * 7.0)) * 0.5;
        uv += wobble * px * Heat * 3.5 * zone;
    }

    // shattered like glass: cells (Voronoi) of the picture each shifted and turned a little, their edges bright
    float shard = 0.0;
    if (Shatter.x > 0.001) {
        float aspect = OutSize.x / OutSize.y;
        vec2 p = uv * vec2(aspect, 1.0) * 4.0;
        vec2 cell = floor(p), f = fract(p);
        float best = 8.0, second = 8.0;
        vec2 bestId = cell;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 g = vec2(float(i), float(j));
                vec2 r = g + hash2(cell + g + Shatter.y) - f;
                float d = dot(r, r);
                if (d < best) {
                    second = best;
                    best = d;
                    bestId = cell + g;
                } else if (d < second) {
                    second = d;
                }
            }
        }
        vec2 h = hash2(bestId * 1.7 + Shatter.y + 3.1) - 0.5;
        float ang = h.x * 0.3 * Shatter.x;
        vec2 c = uv - 0.5;
        c = vec2(c.x * cos(ang) - c.y * sin(ang), c.x * sin(ang) + c.y * cos(ang));
        uv = c + 0.5 + h * 0.05 * Shatter.x;
        shard = 1.0 - smoothstep(0.0, 0.08, sqrt(second) - sqrt(best));
    }

    // pixelate: big blocks
    if (Pixelate > 0.01) {
        vec2 block = vec2(1.0 + Pixelate * 24.0) / OutSize;
        uv = (floor(uv / block) + 0.5) * block;
    }

    // distort: the picture waves and ripples out from the middle
    if (Distort > 0.01) {
        uv.x += sin(uv.y * 18.0 + Time * 3.1) * 0.010 * Distort;
        uv.y += sin(uv.x * 14.0 + Time * 2.3) * 0.008 * Distort;
        vec2 fromMid = uv - 0.5;
        float d = length(fromMid);
        uv += fromMid / max(d, 1e-3) * sin(d * 40.0 - Time * 8.0) * 0.006 * Distort;
    }

    // glitch: bands of the picture jump sideways, now and then
    float split = 0.0;
    if (Glitch > 0.01) {
        float frame = floor(Time * 14.0);
        float band = floor(uv.y * 24.0);
        float h = hash(vec2(band, frame));
        if (h < Glitch * 0.35) {
            uv.x += (hash(vec2(frame, band + 7.0)) - 0.5) * 0.12 * Glitch;
            split = Glitch * 0.012;
        }
    }

    // colours split apart (towards the edges)
    vec2 off = (uv - 0.5) * (Aberration - Rewind * 1.5) * 0.025 + vec2(split, 0.0);
    vec3 color;
    if (length(off) > 1e-5) {
        color = vec3(blurred(uv + off).r, blurred(uv).g, blurred(uv - off).b);
    } else {
        color = blurred(uv);
    }

    // black and white (but for one colour, if asked)
    if (Desaturate > 0.01) {
        float gray = dot(color, vec3(0.299, 0.587, 0.114));
        float keep = 0.0;
        if (KeepColor.a > 0.5) {
            vec3 hc = hue(color), hk = hue(KeepColor.rgb);
            float dh = abs(hc.x - hk.x);
            dh = min(dh, 1.0 - dh);
            keep = (1.0 - smoothstep(0.04, 0.10, dh)) * smoothstep(0.15, 0.35, hc.y);
        }
        color = mix(color, vec3(gray), Desaturate * (1.0 - keep));
    }

    color = mix(color, vec3(1.0) - color, Invert);

    if (Glow.w > 0.001) {
        vec3 b = blurAt(uv, 12.0);
        color += max(b - vec3(0.5), vec3(0.0)) * Glow.rgb * Glow.w * 1.6 + Glow.rgb * Glow.w * 0.04;
    }
    if (Rewind > 0.001) {
        float g = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(g * 1.07, g * 0.92, g * 0.72), Rewind * 0.7);
        color += (hash(floor(texCoord * OutSize / 2.0) + floor(Time * 24.0)) - 0.5) * 0.14 * Rewind;
    }
    if (Timestop > 0.001) {
        float g = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(g) * vec3(0.78, 0.88, 1.06), Timestop * 0.85);
        color += (hash(floor(texCoord * OutSize / 2.0)) - 0.5) * 0.12 * Timestop; // the grain stands still too
        color *= 1.0 - smoothstep(0.3, 0.85, length(texCoord - 0.5)) * 0.8 * Timestop;
    }
    color = mix(color, vec3(1.0), shard * Shatter.x * 0.85);

    if (Noise > 0.01) {
        float n = hash(floor(texCoord * OutSize) + fract(Time * 13.0) * 100.0) - 0.5;
        color += n * Noise * 0.35;
    }

    // only where it is on
    float w = 1.0;
    if (MaskMode > 1.5) {
        w = texture(Sampler1, texCoord).r;
    } else if (MaskMode > 0.5) {
        float d = length((texCoord - Circle.xy) * OutSize);
        w = 1.0 - smoothstep(Circle.z - Circle.w, Circle.z, d);
    }
    if (MaskInvert > 0.5) w = 1.0 - w;
    w = max(w, zone);
    vec3 base = texture(Sampler0, texCoord).rgb;
    color = mix(base, color, w);
    color = mix(color, Flash.rgb, clamp(Flash.a, 0.0, 1.0) * w);

    // a haze round the shape: blurred where the shape is near but not here, fading out
    if (MaskMode > 1.5 && AuraBlur > 0.001) {
        vec2 px = 1.0 / OutSize;
        float here = texture(Sampler1, texCoord).r;
        float near = 0.0;
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5236;
            vec2 dir = vec2(cos(a), sin(a)) * px;
            near = max(near, texture(Sampler1, texCoord + dir * AuraWidth * 0.35).r);
            near = max(near, texture(Sampler1, texCoord + dir * AuraWidth * 0.7).r * 0.75);
            near = max(near, texture(Sampler1, texCoord + dir * AuraWidth).r * 0.45);
        }
        float halo = clamp(near - here, 0.0, 1.0);
        if (halo > 0.001) color = mix(color, blurAt(texCoord, AuraBlur * 16.0), halo);
    }

    // a glowing outline round the shape: where the shape is near but not here
    if (MaskMode > 1.5 && OutlineColor.a > 0.001) {
        vec2 px = 1.0 / OutSize;
        float here = texture(Sampler1, texCoord).r;
        float near = 0.0, glow = 0.0;
        for (int i = 0; i < 16; i++) {
            float a = float(i) * 0.392699;
            vec2 dir = vec2(cos(a), sin(a)) * px;
            near = max(near, texture(Sampler1, texCoord + dir * OutlineWidth).r);
            near = max(near, texture(Sampler1, texCoord + dir * OutlineWidth * 0.5).r);
            glow = max(glow, texture(Sampler1, texCoord + dir * OutlineWidth * 2.5).r);
        }
        float edge = clamp(near - here, 0.0, 1.0);
        float halo = clamp(glow - here, 0.0, 1.0) * 0.45 * (0.8 + 0.2 * sin(Time * 6.0));
        color = mix(color, OutlineColor.rgb, clamp(max(edge, halo) * OutlineColor.a, 0.0, 1.0));
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
