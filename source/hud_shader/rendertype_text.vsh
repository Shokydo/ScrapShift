#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Screen-relative HUD glyph positioning adapted from BetterHud 1.14.1, text.vsh (MIT).
// This subset retains the encoded-height marker and orthographic screen projection.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

const int HEIGHT_BIT = 10;
const int HUD_MARKER = 16;

void main() {
    vec3 pos = Position;
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    bool gui = abs(ProjMat[3].x + 1.0) < 0.01 && ProjMat[1][1] < 0.0;
    vec3 digitMarker = vec3(234.0,149.0,23.0)/255.0;
    bool digit = distance(Color.rgb,digitMarker)<0.006;
    bool digitShadow = distance(Color.rgb,digitMarker*0.25)<0.008;
    if(gui && pos.y < 500.0 && (digit || digitShadow)) {
        vec2 ui=ceil(2.0/vec2(ProjMat[0][0],-ProjMat[1][1]));
        pos.x += 125.0 - 0.5*ui.x;
        pos.y = mod(pos.y,19.0) + 151.0;
        vertexColor.rgb *= vec3(1.0,1.25,2.0);
        if(digitShadow)vertexColor.a=0.0;
    }
    if (abs(ProjMat[3].x + 1.0) < 0.01 && ProjMat[1][1] < 0.0 && pos.y >= 16384.0) {
        vec2 ui = ceil(2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]));
        int bit = int(pos.y) >> HEIGHT_BIT;
        if (bit == HUD_MARKER) {
            pos.y -= float(bit << HEIGHT_BIT);
            pos.x += 83.0 - 0.5 * ui.x;
            pos.y += 13.0;
            if (max(Color.r, max(Color.g, Color.b)) < 0.40) vertexColor.a = 0.0;
        } else if (bit == HUD_MARKER + 6) {
            float row = pos.y - float(bit << HEIGHT_BIT);
            pos.y = 6.0 + mod(row - 9.0,19.0);
            pos.x += 0.5;
            if (max(Color.r,max(Color.g,Color.b)) < 0.40) vertexColor.a = 0.0;
        } else if (bit == HUD_MARKER + 4 || bit == HUD_MARKER + 5) {
            float row=pos.y-float(bit << HEIGHT_BIT);
            // Boss-bar title baselines differ by 19px. Normalize that offset.
            pos.y = ui.y - (bit == HUD_MARKER + 4 ? 122.0 : 95.0) + mod(row - 9.0,19.0) * (bit == HUD_MARKER + 4 ? 3.0 : 1.0);
            pos.x += 0.5;
            if (max(Color.r, max(Color.g, Color.b)) < 0.40) vertexColor.a = 0.0;
        } else if (bit == HUD_MARKER + 3) {
            float row = pos.y - float(bit << HEIGHT_BIT);
            pos.x += 83.0 - 0.5 * ui.x;
            pos.y = mod(row,19.0) > 8.5 ? 148.0 : 177.0;
            if (max(Color.r, max(Color.g, Color.b)) < 0.40) vertexColor.a = 0.0;
        } else if (bit == HUD_MARKER + 2) {
            float row = pos.y - float(bit << HEIGHT_BIT);
            pos.x = pos.x < ui.x * 0.5 ? 0.0 : ui.x;
            pos.y = row < 108.0 ? 0.0 : ui.y;
            if (max(Color.r, max(Color.g, Color.b)) < 0.40) vertexColor.a = 0.0;
        } else if (bit == HUD_MARKER + 1) {
            pos.y -= float(bit << HEIGHT_BIT);
            pos.y += ui.y - 66.0;
            if (max(Color.r, max(Color.g, Color.b)) < 0.40) vertexColor.a = 0.0;
        }
    }
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    texCoord0 = UV0;
}
