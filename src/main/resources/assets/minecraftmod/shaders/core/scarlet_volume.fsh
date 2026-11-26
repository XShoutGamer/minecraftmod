#version 150

uniform float Time;
uniform int Steps;
in vec2 volumeUV;
in vec4 densityTint;
out vec4 fragColor;

float hash(vec3 p) {
    vec3 q = fract(p * vec3(.1031, .1030, .0973));
    q += dot(q, q.yxz + 33.33);
    return fract((q.x + q.y) * q.z);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(cell), hash(cell + vec3(1,0,0)), f.x),
                   mix(hash(cell + vec3(0,1,0)), hash(cell + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(cell + vec3(0,0,1)), hash(cell + vec3(1,0,1)), f.x),
                   mix(hash(cell + vec3(0,1,1)), hash(cell + vec3(1,1,1)), f.x), f.y), f.z);
}

mat2 spin(float a) { return mat2(cos(a), sin(a), -sin(a), cos(a)); }

void main() {
    float r2 = dot(volumeUV, volumeUV);
    if (r2 >= 1.0) discard;

    float depth = sqrt(1.0 - r2);
    float stepSize = 2.0 * depth / float(Steps);
    float transmittance = 1.0;
    vec3 light = vec3(0.0);
    for (int i = 0; i < Steps; i++) {
        float z = -depth + stepSize * (float(i) + .5);
        vec3 p = vec3(volumeUV, z);
        p.xy = spin(z * 1.3 + Time * .13 + densityTint.b * 4.0) * p.xy;
        vec3 q = p * 4.0 + vec3(densityTint.b * 14.0, -Time * .40, Time * .29);
        vec3 warp = vec3(noise(q * .51), noise(q * .59 + 19), noise(q * .56 - 13)) - .5;
        q += 2.6 * warp;
        float eddies = .63 * noise(q) + .26 * noise(q * 2.34) + .11 * noise(q * 5.14);
        float envelope = pow(max(0.0, 1.0 - length(p)), .63);
        float smoke = smoothstep(.23, .69, eddies) * envelope;
        float veins = pow(max(0.0, 1.0 - 4.5 * abs(eddies - .53)), 5.0) * envelope;
        float extinction = smoke * stepSize * (4.2 + 4.0 * densityTint.r);
        float absorbed = 1.0 - exp(-extinction);
        vec3 shade = mix(vec3(.035,.002,.012), vec3(.50,.009,.082), 1.0 - smoothstep(-.3,.76,p.y));
        vec3 emission = vec3(1.0,.065,.20) * veins * (.42 + densityTint.r);
        light += transmittance * (shade * absorbed + emission * stepSize);
        transmittance *= 1.0 - absorbed;
        if (transmittance < .025) break;
    }
    float density = 1.0 - transmittance;
    fragColor = vec4(light / max(.14, density), density * densityTint.a);
}
