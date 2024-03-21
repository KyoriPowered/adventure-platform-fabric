/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2020-2024 KyoriPowered
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
package net.kyori.adventure.platform.fabric.impl.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.kyori.adventure.platform.fabric.impl.AdventureCommon;
import net.kyori.adventure.platform.fabric.impl.ClientboundArgumentTypeMappingsPacket;
import net.kyori.adventure.platform.fabric.impl.ServerArgumentTypes;
import net.kyori.adventure.platform.fabric.impl.ServerboundRegisteredArgumentTypesPacket;

public final class AdventureClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    this.setupCustomArgumentTypes();
  }

  private void setupCustomArgumentTypes() {
    // sync is optional, so fapi is not required
    if (FabricLoader.getInstance().isModLoaded(AdventureCommon.MOD_FAPI_NETWORKING)) {
      C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
        if (channels.contains(ServerboundRegisteredArgumentTypesPacket.TYPE.id())) {
          client.execute(() -> {
            if (ClientPlayNetworking.canSend(ServerboundRegisteredArgumentTypesPacket.TYPE)) {
              ServerboundRegisteredArgumentTypesPacket.of(ServerArgumentTypes.ids()).sendTo(sender);
            }
          });
        }
      });
      ClientPlayNetworking.registerGlobalReceiver(ClientboundArgumentTypeMappingsPacket.TYPE, (pkt, ctx) -> {
        ctx.client().execute(() -> ServerArgumentTypes.receiveMappings(pkt));
      });
    }
  }
}
