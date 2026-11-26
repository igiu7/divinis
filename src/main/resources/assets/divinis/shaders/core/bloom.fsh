#version 150

// Divinis bloom (client/BloomFx): light pouring out of a shape - its mask blurred, then added to the frame as light

uniform sampler2D Sampler0;
uniform float Mode;       // 1: blur the mask along Direction, 2: the blurred mask as light
uniform vec2 Direction;   // one texel along the blur (uv)
uniform float Radius;     // how far the blur reaches each way (texels)
uniform vec3 Color;       // the light's colour
uniform float Strength;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    if (Mode < 1.5) {
        float sum = 0.0, weights = 0.0;
        for (int i = -10; i <= 10; i++) {
            float x = float(i) / 10.0;
            float w = exp(-x * x * 3.0);
            sum += texture(Sampler0, texCoord + Direction * x * Radius).r * w;
            weights += w;
        }
        fragColor = vec4(vec3(sum / weights), 1.0);
    } else {
        // brightest at the body, falling off softly; never past white (it is added to the frame)
        float g = texture(Sampler0, texCoord).r;
        float light = 1.0 - exp(-g * Strength * 2.5);
        fragColor = vec4(Color * light, 1.0);
    }
}
