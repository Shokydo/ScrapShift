#version 330

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // GUI projection uses exact framebuffer/GUI-scale dimensions; layout uses their ceiling.
    // Relocate the final 16x16 atlas quad, not the item render used to produce the atlas.
    if (abs(ProjMat[3].x + 1.0) < 0.01 && ProjMat[1][1] < 0.0) {
        vec2 extent = 2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]);
        vec2 gui = ceil(extent - vec2(0.001));
        vec2 xy = (ModelViewMat * vec4(Position, 1.0)).xy;
        float left = floor(gui.x * 0.5) - 88.0;
        float row = gui.y - 19.0;
        for(int i=0; i<5; ++i){
            vec2 local = xy - vec2(left + 20.0 * float(i), row);
            vec2 edge = min(abs(local), abs(local - vec2(16.0)));
            if(edge.x < 0.06 && edge.y < 0.06){
                vec2 dst = vec2(floor(gui.x*0.5)-70.0+35.0*float(i)+5.0, gui.y-51.0)
                         + local * 1.5;
                if(i==4) dst = vec2(25.0,154.0) + local;
                gl_Position += ProjMat * vec4(dst-xy, 0.0, 0.0);
                break;
            }
        }
    }
    texCoord0 = UV0;
    vertexColor = Color;
}
