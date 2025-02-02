package com.plusls.ommc.feature.highlithtWaypoint;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.context.CommandContext;
import com.plusls.ommc.OhMyMinecraftClientReference;
import com.plusls.ommc.api.command.ClientBlockPosArgument;
import com.plusls.ommc.config.Configs;
import com.plusls.ommc.mixin.accessor.AccessorTextComponent;
import com.plusls.ommc.mixin.accessor.AccessorTranslatableComponent;
import com.plusls.ommc.util.Tuple;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import top.hendrixshen.magiclib.compat.minecraft.api.blaze3d.vertex.VertexFormatCompatApi;
import top.hendrixshen.magiclib.compat.minecraft.api.network.chat.ComponentCompatApi;
import top.hendrixshen.magiclib.compat.minecraft.api.network.chat.StyleCompatApi;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//#if MC > 11902
import com.mojang.math.Axis;
//#else
//$$ import top.hendrixshen.magiclib.compat.minecraft.api.math.Vector3fCompatApi;
//#endif

//#if MC > 11901
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//#endif

//#if MC > 11802
import net.minecraft.network.chat.contents.*;
//#else
//$$ import net.minecraft.client.Option;
//#endif

//#if MC < 11700
//$$ import net.minecraft.client.renderer.texture.TextureAtlas;
//#endif

//#if MC < 11600
//$$ import net.minecraft.world.level.dimension.DimensionType;
//#endif

public class HighlightWaypointUtil {
    private static final String HIGHLIGHT_COMMAND = "highlightWaypoint";
    private static final Tuple<BlockPos, BlockPos> highlightPos = new Tuple<>(null, null);

    public static long lastBeamTime = 0;
    public static Pattern pattern = Pattern.compile("(?:(?:x\\s*:\\s*)?(?<x>(?:[+-]?\\d+)(?:\\.\\d+)?)(?:[df])?)(?:(?:(?:\\s*[,，]\\s*(?:y\\s*:\\s*)?)|(?:\\s+))(?<y>(?:[+-]?\\d+)(?:\\.\\d+)?)(?:[df])?)?(?:(?:(?:\\s*[,，]\\s*(?:z\\s*:\\s*)?)|(?:\\s+))(?<z>(?:[+-]?\\d+)(?:\\.\\d+)?)(?:[df])?)", Pattern.CASE_INSENSITIVE);

    public static void init() {
        //#if MC > 11901
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
        //#else
        //$$ ClientCommandManager.DISPATCHER.register(
        //#endif
            ClientCommandManager.literal(HIGHLIGHT_COMMAND).then(
                    ClientCommandManager.argument("pos", ClientBlockPosArgument.blockPos())
                            .executes(HighlightWaypointUtil::runCommand)
        //#if MC > 11901
            )));
        //#else
        //$$ ));
        //#endif
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HighlightWaypointUtil.clearHighlightPos());
    }

    private static int runCommand(CommandContext<FabricClientCommandSource> context) {
        BlockPos pos = ClientBlockPosArgument.getBlockPos(context, "pos");
        HighlightWaypointUtil.setHighlightPos(pos, false);

        return 0;
    }

    private static @NotNull List<ParseResult> parsePositions(@NotNull String message) {
        List<ParseResult> ret = Lists.newArrayList();
        Matcher matcher = HighlightWaypointUtil.pattern.matcher(message);

        while (matcher.find()) {
            ret.add(HighlightWaypointUtil.parsePosition(matcher));
        }

        ret.removeIf(Objects::isNull);
        ret.sort(Comparator.comparingInt(ParseResult::getMatcherStart));
        return ret;
    }

    private static @Nullable ParseResult parsePosition(@NotNull Matcher matcher) {
        Integer x = null;
        int y = 64;
        Integer z = null;
        String xStr = matcher.group("x");
        String yStr = matcher.group("y");
        String zStr = matcher.group("z");

        try {
            x = xStr.contains(".") ? (int) Double.parseDouble(xStr) : Integer.parseInt(xStr);
            z = zStr.contains(".") ? (int) Double.parseDouble(zStr) : Integer.parseInt(zStr);

            if (yStr != null) {
                y = zStr.contains(".") ? (int) Double.parseDouble(yStr) : Integer.parseInt(yStr);
            }
        } catch (NumberFormatException e) {
            OhMyMinecraftClientReference.getLogger().error("Failed to parse coordinate {}: {}", matcher.group(), e);
        }

        if (x == null || z == null) {
            return null;
        }

        return new ParseResult(matcher.group(), new BlockPos(x, y, z), matcher.start());
    }

    public static void parseMessage(@NotNull Component chat) {
        chat.getSiblings().forEach(HighlightWaypointUtil::parseMessage);
        //#if MC > 11802
        ComponentContents componentContents = chat.getContents();
        //#endif

        if (
                //#if MC > 11802
                !(componentContents instanceof TranslatableContents)
                //#else
                //$$ !(chat instanceof TranslatableComponent)
                //#endif
        ) {
            HighlightWaypointUtil.updateMessage(chat);
            return;
        }

        //#if MC > 11802
        Object[] args = ((TranslatableContents) componentContents).getArgs();
        //#else
        //$$ Object[] args = ((TranslatableComponent) chat).getArgs();
        //#endif
        boolean updateTranslatableText = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component) {
                HighlightWaypointUtil.parseMessage((Component) args[i]);
            } else if (args[i] instanceof String) {
                Component text = ComponentCompatApi.literal((String) args[i]);

                if (HighlightWaypointUtil.updateMessage(text)) {
                    args[i] = text;
                    updateTranslatableText = true;
                }
            }
        }

        if (updateTranslatableText) {
            //#if MC > 11802
            ((AccessorTranslatableComponent) componentContents).setDecomposedWith(null);
            //#elseif MC > 11502
            //$$ ((AccessorTranslatableComponent) chat).setDecomposedWith(null);
            //#else
            //$$ ((AccessorTranslatableComponent) chat).setDecomposedLanguageTime(-1);
            //#endif
        }

        HighlightWaypointUtil.updateMessage(chat);
    }

    public static boolean updateMessage(@NotNull Component chat) {
        //#if MC > 11802
        ComponentContents componentContents = chat.getContents();

        //#endif
        if (
                //#if MC > 11802
                !(componentContents instanceof LiteralContents)
                //#else
                //$$ !(chat instanceof TextComponent)
                //#endif
        ) {
            return false;
        }

        //#if MC > 11802
        LiteralContents literalChatText = (LiteralContents) componentContents;
        //#else
        //$$ TextComponent literalChatText = (TextComponent) chat;
        //#endif
        String message = ((AccessorTextComponent) (Object) literalChatText).getText();
        List<ParseResult> positions = HighlightWaypointUtil.parsePositions(message);

        if (positions.isEmpty()) {
            return false;
        }

        Style originalStyle = chat.getStyle();
        ClickEvent originalClickEvent = originalStyle.getClickEvent();
        ArrayList<Component> texts = Lists.newArrayList();
        int prevIdx = 0;

        // Rebuild components.
        for (ParseResult position : positions) {
            String waypointString = position.getText();
            int waypointIdx = position.getMatcherStart();
            texts.add(ComponentCompatApi.literal(message.substring(prevIdx, waypointIdx)).withStyle(originalStyle));
            BlockPos pos = position.getPos();
            texts.add(ComponentCompatApi.literal(waypointString)
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(style -> style.withClickEvent(originalClickEvent == null ||
                            Configs.forceParseWaypointFromChat ? new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            String.format("/%s %d %d %d", HIGHLIGHT_COMMAND, pos.getX(), pos.getY(), pos.getZ())) :
                            originalClickEvent))
                    .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            ComponentCompatApi.literal(OhMyMinecraftClientReference.translate("highlight_waypoint.tooltip"))))));
            prevIdx = waypointIdx + waypointString.length();
        }

        // Add tail if existed.
        if (prevIdx < message.length()) {
            texts.add(ComponentCompatApi.literal(message.substring(prevIdx)).withStyle(originalStyle));
        }

        texts.forEach(chat.getSiblings()::add);
        ((AccessorTextComponent) (Object) literalChatText).setText("");
        //#if MC > 11502
        ((MutableComponent) chat).withStyle(StyleCompatApi.empty());
        //#else
        //$$ ((BaseComponent) chat).withStyle(StyleCompatApi.empty());
        //#endif
        return true;
    }

    private static double getDistanceToEntity(@NotNull Entity entity, @NotNull BlockPos pos) {
        double dx = pos.getX() + 0.5 - entity.getX();
        double dy = pos.getY() + 0.5 - entity.getY();
        double dz = pos.getZ() + 0.5 - entity.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isPointedAt(@NotNull BlockPos pos, double distance, @NotNull Entity cameraEntity, float tickDelta) {
        Vec3 cameraPos = cameraEntity.getEyePosition(tickDelta);
        double degrees = 5.0 + Math.min((5.0 / distance), 5.0);
        double angle = degrees * 0.0174533;
        double size = Math.sin(angle) * distance;
        Vec3 cameraPosPlusDirection = cameraEntity.getViewVector(tickDelta);
        Vec3 cameraPosPlusDirectionTimesDistance = cameraPos.add(cameraPosPlusDirection.x() * distance, cameraPosPlusDirection.y() * distance, cameraPosPlusDirection.z() * distance);
        AABB axisalignedbb = new AABB(pos.getX() + 0.5f - size, pos.getY() + 0.5f - size, pos.getZ() + 0.5f - size,
                pos.getX() + 0.5f + size, pos.getY() + 0.5f + size, pos.getZ() + 0.5f + size);
        Optional<Vec3> raycastResult = axisalignedbb.clip(cameraPos, cameraPosPlusDirectionTimesDistance);
        return axisalignedbb.contains(cameraPos) ? distance >= 1.0 : raycastResult.isPresent();
    }

    public static void drawWaypoint(PoseStack matrixStack, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) {
            return;
        }

        BlockPos pos = HighlightWaypointUtil.inNether(player) ?
                HighlightWaypointUtil.highlightPos.getB() : HighlightWaypointUtil.highlightPos.getA();

        if (pos == null) {
            return;
        }

        Entity cameraEntity = mc.getCameraEntity();

        if (cameraEntity == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        double distance = getDistanceToEntity(cameraEntity, pos);
        HighlightWaypointUtil.renderLabel(matrixStack, distance, cameraEntity, tickDelta,
                HighlightWaypointUtil.isPointedAt(pos, distance, cameraEntity, tickDelta), pos);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // code from BeaconBlockEntityRenderer
    @SuppressWarnings("all")
    public static void renderBeam(@NotNull PoseStack matrices, float tickDelta, float heightScale, long worldTime,
                                  int yOffset, int maxY, float @NotNull [] color, float innerRadius, float outerRadius) {
        ResourceLocation textureId = new ResourceLocation("textures/entity/beacon_beam.png");
        //#if MC > 11605
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);
        //#else
        //$$ Minecraft.getInstance().getTextureManager().bind(textureId);
        //#endif
        int i = yOffset + maxY;
        matrices.pushPose();
        matrices.translate(0.5D, 0.0D, 0.5D);
        float f = (float) Math.floorMod(worldTime, 40L) + tickDelta;
        float g = maxY < 0 ? f : -f;
        float h = (float) Mth.frac(g * 0.2F - (float) Mth.floor(g * 0.1F));
        float red = color[0];
        float green = color[1];
        float blue = color[2];
        matrices.pushPose();
        //#if MC >= 11903
        matrices.mulPose(Axis.YP.rotationDegrees(f * 2.25F - 45.0F));
        //#else
        //$$ matrices.mulPose(Vector3fCompatApi.YP.rotationDegrees(f * 2.25F - 45.0F));
        //#endif
        float y = 0.0F;
        float ab = 0.0F;
        float ac = -innerRadius;
        float r = 0.0F;
        float s = 0.0F;
        float t = -innerRadius;
        float ag = 0.0F;
        float ah = 1.0F;
        float ai = -1.0F + h;
        float aj = (float) maxY * heightScale * (0.5F / innerRadius) + ai;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormatCompatApi.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        renderBeamLayer(matrices, bufferBuilder, red, green, green, 1.0F, yOffset, i, 0.0F, innerRadius, innerRadius,
                0.0F, ac, 0.0F, 0.0F, t, 0.0F, 1.0F, aj, ai);
        tesselator.end();
        matrices.popPose();
        y = -outerRadius;
        float z = -outerRadius;
        ab = -outerRadius;
        ac = -outerRadius;
        ag = 0.0F;
        ah = 1.0F;
        ai = -1.0F + h;
        aj = (float) maxY * heightScale + ai;
        bufferBuilder.begin(VertexFormatCompatApi.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        renderBeamLayer(matrices, bufferBuilder, red, green, green, 0.125F, yOffset, i, y, z, outerRadius, ab, ac, outerRadius, outerRadius, outerRadius, 0.0F, 1.0F, aj, ai);
        tesselator.end();
        matrices.popPose();
        //#if MC > 11605
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //#endif
    }

    private static void renderBeamFace(Matrix4f modelMatrix, Matrix3f normalMatrix, BufferBuilder vertices, float red, float green, float blue, float alpha, int yOffset, int height, float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        renderBeamVertex(modelMatrix, normalMatrix, vertices, red, green, blue, alpha, height, x1, z1, u2, v1);
        renderBeamVertex(modelMatrix, normalMatrix, vertices, red, green, blue, alpha, yOffset, x1, z1, u2, v2);
        renderBeamVertex(modelMatrix, normalMatrix, vertices, red, green, blue, alpha, yOffset, x2, z2, u1, v2);
        renderBeamVertex(modelMatrix, normalMatrix, vertices, red, green, blue, alpha, height, x2, z2, u1, v1);
    }

    private static void renderBeamVertex(Matrix4f modelMatrix, Matrix3f normalMatrix, @NotNull BufferBuilder vertices, float red, float green, float blue, float alpha, int y, float x, float z, float u, float v) {
        vertices.vertex(modelMatrix, x, (float) y, z)
                .uv(u, v)
                .color(red, green, blue, alpha).endVertex();
    }

    @SuppressWarnings("all")
    private static void renderBeamLayer(@NotNull PoseStack matrices, BufferBuilder vertices, float red, float green, float blue, float alpha, int yOffset, int height, float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4, float u1, float u2, float v1, float v2) {
        PoseStack.Pose entry = matrices.last();
        Matrix4f matrix4f = entry.pose();
        Matrix3f matrix3f = entry.normal();
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }

    public static void renderLabel(PoseStack matrixStack, double distance, @NotNull Entity cameraEntity, float tickDelta, boolean isPointedAt, @NotNull BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        String name = String.format("x:%d, y:%d, z:%d (%dm)", pos.getX(), pos.getY(), pos.getZ(), (int) distance);
        double baseX = pos.getX() - Mth.lerp(tickDelta, cameraEntity.xo, cameraEntity.getX());
        double baseY = pos.getY() - Mth.lerp(tickDelta, cameraEntity.yo, cameraEntity.getY()) - 1.5;
        double baseZ = pos.getZ() - Mth.lerp(tickDelta, cameraEntity.zo, cameraEntity.getZ());
        // Max render distance
        //#if MC > 11802
        double maxDistance = Minecraft.getInstance().options.renderDistance().get() * 16;
        //#else
        //$$ double maxDistance = Option.RENDER_DISTANCE.get(mc.options) * 16;
        //#endif
        double adjustedDistance = distance;

        if (distance > maxDistance) {
            baseX = baseX / distance * maxDistance;
            baseY = baseY / distance * maxDistance;
            baseZ = baseZ / distance * maxDistance;
            adjustedDistance = maxDistance;
        }

        // 根据调节后的距离决定绘制的大小
        float scale = (float) (adjustedDistance * 0.1f + 1.0f) * 0.0265f;
        matrixStack.pushPose();
        // 当前绘制位置是以玩家为中心的，转移到目的地
        matrixStack.translate(baseX, baseY, baseZ);

        if (lastBeamTime >= System.currentTimeMillis()) {
            // 画信标光柱
            float[] color = {1.0f, 0.0f, 0.0f};
            renderBeam(matrixStack, tickDelta, 1.0f,
                    Objects.requireNonNull(mc.level).getGameTime(),
                    (int) (baseY - 512), 1024, color, 0.2F, 0.25F);

            // 画完后会关闭半透明，需要手动打开
            RenderSystem.enableBlend();
        }

        // 移动到方块中心
        matrixStack.translate(0.5f, 0.5f, 0.5f);

        // 在玩家正对着的平面进行绘制
        //#if MC >= 11903
        matrixStack.mulPose(Axis.YP.rotationDegrees(-cameraEntity.getYRot()));
        matrixStack.mulPose(Axis.XP.rotationDegrees(mc.getEntityRenderDispatcher().camera.getXRot()));
        //#else
        //$$ matrixStack.mulPose(Vector3fCompatApi.YP.rotationDegrees(-cameraEntity.getYRot()));
        //$$ matrixStack.mulPose(Vector3fCompatApi.XP.rotationDegrees(mc.getEntityRenderDispatcher().camera.getXRot()));
        //#endif
        // 缩放绘制的大小，让 waypoint 根据距离缩放
        matrixStack.scale(-scale, -scale, -scale);
        Matrix4f matrix4f = matrixStack.last().pose();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder vertexBuffer = tessellator.getBuilder();
        // 透明度
        float fade = distance < 5.0 ? 1.0f : (float) distance / 5.0f;
        fade = Math.min(fade, 1.0f);
        // 渲染的图标的大小
        float xWidth = 10.0f;
        float yWidth = 10.0f;
        // 绿色
        float iconR = 1.0f;
        float iconG = 0.0f;
        float iconB = 0.0f;
        float textFieldR = 3.0f;
        float textFieldG = 0.0f;
        float textFieldB = 0.0f;
        // 图标
        TextureAtlasSprite icon = HighlightWaypointResourceLoader.targetIdSprite;
        // 不设置渲染不出
        //#if MC > 11605
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        //#else
        //$$ RenderSystem.bindTexture(Objects.requireNonNull(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)).getId());
        //#endif

        // 渲染图标
        //#if MC < 11904
        //$$ RenderSystem.enableTexture();
        //#endif
        vertexBuffer.begin(VertexFormatCompatApi.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        vertexBuffer.vertex(matrix4f, -xWidth, -yWidth, 0.0f).uv(icon.getU0(), icon.getV0()).color(iconR, iconG, iconB, fade).endVertex();
        vertexBuffer.vertex(matrix4f, -xWidth, yWidth, 0.0f).uv(icon.getU0(), icon.getV1()).color(iconR, iconG, iconB, fade).endVertex();
        vertexBuffer.vertex(matrix4f, xWidth, yWidth, 0.0f).uv(icon.getU1(), icon.getV1()).color(iconR, iconG, iconB, fade).endVertex();
        vertexBuffer.vertex(matrix4f, xWidth, -yWidth, 0.0f).uv(icon.getU1(), icon.getV0()).color(iconR, iconG, iconB, fade).endVertex();
        tessellator.end();
        //#if MC < 11904
        //$$ RenderSystem.disableTexture();
        //#endif

        Font textRenderer = mc.font;
        if (isPointedAt && textRenderer != null) {
            // 渲染高度
            int elevateBy = -19;
            RenderSystem.enablePolygonOffset();
            int halfStringWidth = textRenderer.width(name) / 2;
            //#if MC > 11605
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            //#endif

            // 渲染内框
            RenderSystem.polygonOffset(1.0f, 11.0f);
            vertexBuffer.begin(VertexFormatCompatApi.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            vertexBuffer.vertex(matrix4f, -halfStringWidth - 2, -2 + elevateBy, 0.0f).color(textFieldR, textFieldG, textFieldB, 0.6f * fade).endVertex();
            vertexBuffer.vertex(matrix4f, -halfStringWidth - 2, 9 + elevateBy, 0.0f).color(textFieldR, textFieldG, textFieldB, 0.6f * fade).endVertex();
            vertexBuffer.vertex(matrix4f, halfStringWidth + 2, 9 + elevateBy, 0.0f).color(textFieldR, textFieldG, textFieldB, 0.6f * fade).endVertex();
            vertexBuffer.vertex(matrix4f, halfStringWidth + 2, -2 + elevateBy, 0.0f).color(textFieldR, textFieldG, textFieldB, 0.6f * fade).endVertex();
            tessellator.end();

            // 渲染外框
            RenderSystem.polygonOffset(1.0f, 9.0f);
            vertexBuffer.begin(VertexFormatCompatApi.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            vertexBuffer.vertex(matrix4f, -halfStringWidth - 1, -1 + elevateBy, 0.0f).color(0.0f, 0.0f, 0.0f, 0.15f * fade).endVertex();
            vertexBuffer.vertex(matrix4f, -halfStringWidth - 1, 8 + elevateBy, 0.0f).color(0.0f, 0.0f, 0.0f, 0.15f * fade).endVertex();
            vertexBuffer.vertex(matrix4f, halfStringWidth + 1, 8 + elevateBy, 0.0f).color(0.0f, 0.0f, 0.0f, 0.15f * fade).endVertex();
            vertexBuffer.vertex(matrix4f, halfStringWidth + 1, -1 + elevateBy, 0.0f).color(0.0f, 0.0f, 0.0f, 0.15f * fade).endVertex();
            tessellator.end();
            RenderSystem.disablePolygonOffset();

            // 渲染文字
            //#if MC < 11904
            //$$ RenderSystem.enableTexture();
            //#endif
            int textColor = (int) (255.0f * fade) << 24 | 0xCCCCCC;
            RenderSystem.disableDepthTest();
            textRenderer.drawInBatch(ComponentCompatApi.literal(name), (float) (-textRenderer.width(name) / 2), elevateBy, textColor, false, matrix4f, true, 0, 0xF000F0);
        }
        //#if MC > 11605
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        //#endif
        matrixStack.popPose();
        // 1.14 need enableTexture
        //#if MC < 11904
        //$$ RenderSystem.enableTexture();
        //#endif
    }

    public static void setHighlightPos(@NotNull BlockPos pos, boolean directHighlight) {
        Player player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        boolean posChanged;

        if (HighlightWaypointUtil.inOverworld(player)) {
            posChanged = HighlightWaypointUtil.setHighlightBlockPos(
                    pos, new BlockPos(pos.getX() / 8, pos.getY(), pos.getZ() / 8));
        } else if (HighlightWaypointUtil.inNether(player)) {
            posChanged = HighlightWaypointUtil.setHighlightBlockPos(
                    new BlockPos(pos.getX() * 8, pos.getY(), pos.getZ() * 8), pos);
        } else {
            posChanged = HighlightWaypointUtil.setHighlightBlockPos(pos, pos);
        }

        if (directHighlight || !posChanged) {
            HighlightWaypointUtil.lastBeamTime = System.currentTimeMillis() + Configs.highlightBeamTime * 1000L;
        }
    }

    public static BlockPos getHighlightPos() {
        Player player = Minecraft.getInstance().player;
        return player == null ? BlockPos.ZERO : HighlightWaypointUtil.getHighlightPos(player);
    }

    public static BlockPos getHighlightPos(Player player) {
        return HighlightWaypointUtil.inNether(player) ?
                HighlightWaypointUtil.highlightPos.getB() : HighlightWaypointUtil.highlightPos.getA();
    }

    private static boolean setHighlightBlockPos(@NotNull BlockPos overworldPos, @NotNull BlockPos netherWorldPos) {
        if (overworldPos.equals(HighlightWaypointUtil.highlightPos.getA()) &&
                netherWorldPos.equals(HighlightWaypointUtil.highlightPos.getB())) {
            return false;
        }

        HighlightWaypointUtil.highlightPos.setA(overworldPos);
        HighlightWaypointUtil.highlightPos.setB(netherWorldPos);
        return true;
    }

    public static void clearHighlightPos() {
        HighlightWaypointUtil.highlightPos.setA(null);
        HighlightWaypointUtil.highlightPos.setB(null);
        HighlightWaypointUtil.lastBeamTime = 0;
    }

    private static boolean inOverworld(@NotNull Player player) {
        return
                //#if MC > 11502
                player.getLevelCompat().dimension() == Level.OVERWORLD;
                //#else
                //$$ player.getLevelCompat().getDimension().getType() == DimensionType.OVERWORLD;
                //#endif
    }

    private static boolean inNether(@NotNull Player player) {
        return
                //#if MC > 11502
                player.getLevelCompat().dimension() == Level.NETHER;
                //#else
                //$$ player.getLevelCompat().getDimension().getType() == DimensionType.NETHER;
                //#endif
    }

    @Getter
    @AllArgsConstructor
    public static class ParseResult {
        private final String text;
        private final BlockPos pos;
        private final int matcherStart;
    }
}
