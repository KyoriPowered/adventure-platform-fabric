/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2023-2024 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.adventure.platform.fabric.impl.mixin.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.platform.fabric.impl.GameEnums;
import net.kyori.adventure.platform.fabric.impl.server.FabricServerAudiencesImpl;
import net.kyori.adventure.platform.fabric.impl.server.ServerCommonPacketListenerImplBridge;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin implements ServerCommonPacketListenerImplBridge {
  private static final ComponentLogger ADVENTURE$LOGGER = ComponentLogger.logger();
  private final Map<UUID, PackCallbackState> adventure$activeCallbacks = new ConcurrentHashMap<>();

  // @formatter:off
  @Shadow @Final protected Connection connection;
  @Shadow protected abstract GameProfile shadow$playerProfile();
  // @formatter:on

  @Override
  public void adventure$bridge$registerPackCallback(final @NotNull UUID id, final @NotNull FabricServerAudiencesImpl controller, final @NotNull ResourcePackCallback cb) {
    if (this.adventure$activeCallbacks.put(id, new PackCallbackState(controller, cb)) != null) {
      ADVENTURE$LOGGER.warn("Duplicate in-flight callbacks detected for pack {} on player {}", id, this.shadow$playerProfile());
    }
  }

  @Inject(
    method = "handleResourcePackResponse(Lnet/minecraft/network/protocol/common/ServerboundResourcePackPacket;)V",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ServerboundResourcePackPacket;action()Lnet/minecraft/network/protocol/common/ServerboundResourcePackPacket$Action;"),
    cancellable = true
  )
  private void adventure$handleResourcePackCallback(final ServerboundResourcePackPacket pkt, final CallbackInfo ci) {
    final PackCallbackState state;
    if (pkt.action().isTerminal()) {
      state = this.adventure$activeCallbacks.remove(pkt.id());
    } else {
      state = this.adventure$activeCallbacks.get(pkt.id());
    }

    if (state != null) { // adventure-tracked
      state.cb().packEventReceived(
        pkt.id(),
        GameEnums.RESOURCE_PACK_STATUS.toAdventure(pkt.action()),
        state.controller().player(this.shadow$playerProfile().getId())
      );
      ci.cancel();
    }
  }
}
