#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Exposure;
uniform float Monochrome;
uniform float Warp;
uniform float Haze;
uniform float Motion;
uniform float FocusX;
uniform float FocusY;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;

vec3 scene(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, 0.001, 0.999)).rgb;
}

void main() {
    vec2 focus = vec2(FocusX, FocusY);
    vec2 fromLight = texCoord - focus;
    vec2 aspect = vec2(InSize.x / max(1.0, InSize.y), 1.0);
    float radius = length(fromLight * aspect);
    vec2 direction = fromLight / max(0.01, radius);
    float wave = sin(radius * 30.0 - Time * 6.283185) * exp(-radius * 1.35);
    vec2 uv = texCoord + direction * (Warp * wave * 0.022);
    vec3 color = scene(uv);
    if (Warp > .002) {
        color.r = mix(color.r, scene(uv + direction * Warp * .006).r, .44);
        color.b = mix(color.b, scene(uv - direction * Warp * .006).b, .44);
    }

    float blur = Motion * 0.031;
    vec3 radial = scene(uv + fromLight * blur * 0.35)
                + scene(uv - fromLight * blur * 0.45)
                + scene(uv - fromLight * blur);
    color = mix(color, radial / 3.0, Motion * 0.48);

    vec2 px = 1.0 / InSize;
    vec3 nearby = scene(uv + vec2(px.x * 5.0, 0.0)) + scene(uv - vec2(px.x * 5.0, 0.0))
                + scene(uv + vec2(0.0, px.y * 5.0)) + scene(uv - vec2(0.0, px.y * 5.0));
    vec3 bloom = max(vec3(0.0), nearby * 0.25 - vec3(0.56)) * max(0.0, Haze + Exposure) * 0.62;
    float shafts = exp(-radius * 1.9) * max(0.0, Haze) * 0.20;
    color *= 1.0 + Exposure * 0.48 + min(Haze, 0.0) * 0.68;
    color += bloom * vec3(0.68, 0.89, 1.0) + shafts * vec3(0.50, 0.68, 0.86);
    color = mix(color, vec3(0.83, 0.96, 1.0), clamp(Exposure * (0.7 + exp(-radius * 3.5) * 0.27), 0.0, 0.88));
    vec3 original = scene(texCoord);
    float luminance = dot(original, vec3(0.2126, 0.7152, 0.0722));
    color = mix(color, vec3(luminance), Monochrome);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
