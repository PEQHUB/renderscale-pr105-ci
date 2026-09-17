#version 420
//#version 150

#define A_GPU 1
#define A_GLSL 1

// Use the portable 32-bit implementation initially.
#define FSR_EASU_F 1

#moj_import <minecraft:globals.glsl>
#moj_import <renderscale:ffx_a.glsl>

uniform sampler2D InSampler;

out vec4 fragColor;

#ifdef RENDERSCALE_EXPLICIT_GATHER
// RenderPearl translates OpenGL shaders to GLSL 330, where SPIRV-Cross cannot
// emit textureGather with a nonzero component. Match its four texels explicitly.
vec4 renderScaleGather(vec2 p, int component)
{
    ivec2 size = textureSize(InSampler, 0);
    ivec2 base = ivec2(floor(p * vec2(size) - 0.5));
    ivec2 maximum = size - ivec2(1);
    return vec4(
        texelFetch(InSampler, clamp(base + ivec2(0, 1), ivec2(0), maximum), 0)[component],
        texelFetch(InSampler, clamp(base + ivec2(1, 1), ivec2(0), maximum), 0)[component],
        texelFetch(InSampler, clamp(base + ivec2(1, 0), ivec2(0), maximum), 0)[component],
        texelFetch(InSampler, clamp(base, ivec2(0), maximum), 0)[component]
    );
}
#define RENDERSCALE_GATHER(p, component) renderScaleGather(p, component)
#else
#define RENDERSCALE_GATHER(p, component) textureGather(InSampler, p, component)
#endif

/*
 * These callbacks must exist before importing ffx_fsr1.glsl.
 *
 * Minecraft samplers are normally combined sampler2D objects, unlike
 * AMD's Vulkan example, which declares separate texture and sampler objects.
 */
AF4 FsrEasuRF(AF2 p)
{
return RENDERSCALE_GATHER(p, 0);
}

AF4 FsrEasuGF(AF2 p)
{
return RENDERSCALE_GATHER(p, 1);
}

AF4 FsrEasuBF(AF2 p)
{
return RENDERSCALE_GATHER(p, 2);
}

#moj_import <renderscale:ffx_fsr1.glsl>

void main()
{
AU2 outputPixel = AU2(gl_FragCoord.xy);
vec2 inputSize = vec2(textureSize(InSampler, 0));
AU4 fsrConst0;
AU4 fsrConst1;
AU4 fsrConst2;
AU4 fsrConst3;

FsrEasuCon(
fsrConst0,
fsrConst1,
fsrConst2,
fsrConst3,
inputSize.x,
inputSize.y,
inputSize.x,
inputSize.y,
ScreenSize.x,
ScreenSize.y
);

AF3 colour;
FsrEasuF(
colour,
outputPixel,
fsrConst0,
fsrConst1,
fsrConst2,
fsrConst3
);

fragColor = vec4(colour, 1.0);
}
