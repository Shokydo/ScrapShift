#version 150

uniform sampler2D ScreenSampler;
in vec2 texCoord;
out vec4 fragColor;

float grain(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

void main() {
    vec4 scene = texture(ScreenSampler, texCoord);
    // Stable coarse grain; the HUD is composited after this world pass.
    vec2 px = floor(gl_FragCoord.xy);
    float coarse = grain(floor(px / 2.0)) - 0.5;
    float fine = grain(px + vec2(19.0, 73.0)) - 0.5;
    float luma = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    float strength = mix(0.72, 1.0, 1.0 - abs(2.0 * luma - 1.0));
    float noise = (0.056 * coarse + 0.017 * fine) * strength;
    float line = 0.004 * cos(px.y * 2.094395);
    fragColor = vec4(clamp(scene.rgb + vec3(noise - line * luma), 0.0, 1.0), scene.a);
}
