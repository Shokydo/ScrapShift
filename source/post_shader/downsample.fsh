#version 150
uniform sampler2D ScreenSampler;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    vec2 size = vec2(textureSize(ScreenSampler,0));
    float cell = max(2.0, floor(size.y / 270.0 + 0.5));
    vec2 samplePixel = floor((texCoord * size) / cell) * cell + floor(cell * 0.5);
    fragColor = texelFetch(ScreenSampler, ivec2(clamp(samplePixel, vec2(0.0), size - 1.0)), 0);
}
