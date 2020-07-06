/*******************************************************************************
 * Copyright 2019 grondag
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package brcb;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.rendereregistry.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class BlockRenderContextBug implements ModInitializer {

    @Override
    public void onInitialize() {

        // Register a block that renders with a BlockEntityRenderer
        TestBlock block = new TestBlock(AbstractBlock.Settings.copy(Blocks.RED_WOOL));
        Registry.register(Registry.BLOCK, "brcb:testblock", block);
        Registry.register(Registry.ITEM, "brcb:testblock", new BlockItem(block, new Item.Settings()));
        TestBlockEntity.TYPE = BlockEntityType.Builder.create(TestBlockEntity::new, block).build(null);
        Registry.register(Registry.BLOCK_ENTITY_TYPE, "brcb:testblock", TestBlockEntity.TYPE);
        BlockEntityRendererRegistry.INSTANCE.register(TestBlockEntity.TYPE, TestBlockEntityRenderer::new);

        // Register a block that renders without a BlockEntityRenderer
        Block noBerBlock = new Block(AbstractBlock.Settings.copy(Blocks.RED_WOOL));
        Registry.register(Registry.BLOCK, "brcb:testblock_no_ber", noBerBlock);
        Registry.register(Registry.ITEM, "brcb:testblock_no_ber", new BlockItem(noBerBlock, new Item.Settings()));

        // Use our FabricBakedModel for everything
        ModelLoadingRegistry.INSTANCE.registerVariantProvider(manager -> ((modelId, context) -> {
            if (modelId.getNamespace().equals("brcb")) {
                return new TestModel();
            } else {
                return null;
            }
        }));
    }
}

class TestBlock extends Block implements BlockEntityProvider {
    public TestBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState blockState) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockView blockView) {
        return new TestBlockEntity();
    }

}

class TestBlockEntity extends BlockEntity {

    public static BlockEntityType<TestBlockEntity> TYPE;

    public TestBlockEntity() {
        super(TYPE);
    }
}

class TestBlockEntityRenderer extends BlockEntityRenderer<TestBlockEntity> {

    public TestBlockEntityRenderer(BlockEntityRenderDispatcher blockEntityRenderDispatcher) {
        super(blockEntityRenderDispatcher);
    }

    @Override
    public void render(TestBlockEntity blockEntity, float f, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, int j) {

        Block testBlock = Registry.BLOCK.get(new Identifier("brcb:testblock"));
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState state = testBlock.getDefaultState();
        BakedModel testModel = blockRenderManager.getModel(state);

        BlockModelRenderer renderer = blockRenderManager.getModelRenderer();
        VertexConsumer buffer = vertexConsumerProvider.getBuffer(RenderLayer.getSolid());
        renderer.render(blockEntity.getWorld(), testModel, state, blockEntity.getPos(), matrixStack, buffer, false, new Random(), 42L, j);

    }
}

class TestModel implements UnbakedModel {

    @Override
    public Collection<Identifier> getModelDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Collection<SpriteIdentifier> getTextureDependencies(Function<Identifier, UnbakedModel> function, Set<Pair<String, String>> set) {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public BakedModel bake(ModelLoader modelLoader, Function<SpriteIdentifier, Sprite> function, ModelBakeSettings modelBakeSettings, Identifier identifier) {
        return new TestBakedModel(modelLoader.bake(new Identifier("minecraft:block/red_wool"), modelBakeSettings));
    }

}

class TestBakedModel extends ForwardingBakedModel implements FabricBakedModel {

    public TestBakedModel(BakedModel base) {
        this.wrapped = base;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {

        // Emitting a single quad using the normal emitter will make the vanilla model show up, but it MUST be a model!
        MeshBuilder builder = RendererAccess.INSTANCE.getRenderer().meshBuilder();
        builder.getEmitter().square(Direction.UP, 0, 0, 0.00000001f, 0.00000001f, 0).emit();
        context.meshConsumer().accept(builder.build());

        // Just apply a fraky random rotation. Basically anything that uses the pushTransform system
        Random random = randomSupplier.get();
        Quaternion q = new Quaternion(random.nextFloat() * 360f, random.nextFloat() * 360f, random.nextFloat() * 360f, true);
        context.pushTransform(quad -> {
            Vector3f p = new Vector3f();
            for (int i = 0; i < 4; i++) {
                p = quad.copyPos(i, p);
                p.add(-.5f, -.5f, -.5f);
                p.rotate(q);
                p.add(.5f, .5f, .5f);
                quad.pos(i, p);

                quad.cullFace(null);
            }
            return true;
        });
        context.fallbackConsumer().accept(this.wrapped);
        context.popTransform();

    }

}
