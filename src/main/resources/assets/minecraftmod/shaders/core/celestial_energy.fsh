#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in float distanceFromCamera;
in vec4 energy;
in vec2 uv;
out vec4 fragColor;

void main() {
    vec4 tint = texture(Sampler0, uv) * energy * ColorModulator;
    float distanceFade = 1.0 - smoothstep(180.0, 690.0, distanceFromCamera);
    vec3 scattering = pow(max(tint.rgb, vec3(0.0)), vec3(1.08));
    fragColor = vec4(scattering * (0.07 + 0.93 * distanceFade), tint.a);
}
