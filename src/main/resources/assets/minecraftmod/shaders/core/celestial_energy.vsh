#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float distanceFromCamera;
out vec4 energy;
out vec2 uv;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    distanceFromCamera = length(view.xyz);
    energy = Color;
    uv = UV0;
}
