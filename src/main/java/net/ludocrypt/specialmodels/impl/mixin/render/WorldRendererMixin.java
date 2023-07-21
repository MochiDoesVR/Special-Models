package net.ludocrypt.specialmodels.impl.mixin.render;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.ludocrypt.specialmodels.api.SpecialModelRenderer;
import net.ludocrypt.specialmodels.impl.SpecialModels;
import net.ludocrypt.specialmodels.impl.access.WorldChunkBuilderAccess;
import net.ludocrypt.specialmodels.impl.access.WorldRendererAccess;
import net.ludocrypt.specialmodels.impl.chunk.SpecialChunkBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ShaderProgram;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

@Mixin(value = WorldRenderer.class, priority = 900)
public abstract class WorldRendererMixin implements WorldRendererAccess, WorldChunkBuilderAccess {

	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private ClientWorld world;

	@Shadow
	@Final
	private BufferBuilderStorage bufferBuilders;

	@Override
	public void render(MatrixStack matrices, Matrix4f positionMatrix, float tickDelta, Camera camera) {

		SpecialModelRenderer.SPECIAL_MODEL_RENDERER.forEach((modelRenderer) -> this.sortLayer(camera.getPos().getX(), camera.getPos().getY(), camera.getPos().getZ(), modelRenderer));

		ObjectListIterator<SpecialChunkBuilder.ChunkInfo> chunkInfos = this.getSpecialChunkInfoList().listIterator(this.getSpecialChunkInfoList().size());

		while (chunkInfos.hasPrevious()) {
			SpecialChunkBuilder.ChunkInfo chunkInfo = chunkInfos.previous();
			SpecialChunkBuilder.BuiltChunk builtChunk = chunkInfo.chunk;
			builtChunk.getSpecialModelBuffers().forEach(
					(modelRenderer, vertexBuffer) -> specialModels$renderBuffer(matrices, tickDelta, camera, positionMatrix, modelRenderer, vertexBuffer, builtChunk.getOrigin().toImmutable()));
		}

	}

	@Unique
	public void specialModels$renderBuffer(MatrixStack matrices, float tickDelta, Camera camera, Matrix4f positionMatrix, SpecialModelRenderer modelRenderer, VertexBuffer vertexBuffer,
			BlockPos origin) {
		ShaderProgram shader = SpecialModels.LOADED_SHADERS.get(modelRenderer);
		if (shader != null && ((VertexBufferAccessor) vertexBuffer).getIndexCount() > 0) {
			RenderSystem.depthMask(true);
			RenderSystem.enableBlend();
			RenderSystem.enableDepthTest();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
					GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			RenderSystem.polygonOffset(3.0F, 3.0F);
			RenderSystem.enablePolygonOffset();
			RenderSystem.setShader(() -> shader);
			client.gameRenderer.getLightmapTextureManager().enable();

			vertexBuffer.bind();

			Matrix4f viewMatrix = modelRenderer.viewMatrix(new Matrix4f(matrices.peek().getModel()));
			Matrix4f projectionMatrix = modelRenderer.positionMatrix(new Matrix4f(positionMatrix));

			modelRenderer.setup(matrices, new Matrix4f(viewMatrix), new Matrix4f(projectionMatrix), tickDelta, shader);
			if (origin != null) {
				if (shader.chunkOffset != null) {
					BlockPos blockPos = origin;
					float vx = (float) (blockPos.getX() - camera.getPos().getX());
					float vy = (float) (blockPos.getY() - camera.getPos().getY());
					float vz = (float) (blockPos.getZ() - camera.getPos().getZ());
					shader.chunkOffset.setVec3(vx, vy, vz);
				}
			}

			vertexBuffer.draw(viewMatrix, projectionMatrix, shader);

			VertexBuffer.unbind();

			client.gameRenderer.getLightmapTextureManager().disable();

			RenderSystem.polygonOffset(0.0F, 0.0F);
			RenderSystem.disablePolygonOffset();
			RenderSystem.disableBlend();
		}
	}

	@Shadow
	abstract void renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers);

}
