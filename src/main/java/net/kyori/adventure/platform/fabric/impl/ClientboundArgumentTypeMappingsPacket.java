/*
 * This file is part of adventure-platform-fabric, licensed under the MIT License.
 *
 * Copyright (c) 2022-2024 KyoriPowered
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
package net.kyori.adventure.platform.fabric.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.platform.fabric.impl.AdventureCommon.res;

public record ClientboundArgumentTypeMappingsPacket(Int2ObjectMap<ResourceLocation> mappings) implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<ClientboundArgumentTypeMappingsPacket> TYPE = new Type<>(res("registered_arg_mappings"));
  private static final StreamCodec<RegistryFriendlyByteBuf, ClientboundArgumentTypeMappingsPacket> CODEC = StreamCodec.of(
    ClientboundArgumentTypeMappingsPacket::encode,
    ClientboundArgumentTypeMappingsPacket::decode
  );

  public static void register() {
    PayloadTypeRegistry.playS2C().register(ClientboundArgumentTypeMappingsPacket.TYPE, ClientboundArgumentTypeMappingsPacket.CODEC);
  }

  public static ClientboundArgumentTypeMappingsPacket decode(final FriendlyByteBuf buffer) {
    final Int2ObjectMap<ResourceLocation> map = buffer.readMap(
      Int2ObjectArrayMap::new,
      FriendlyByteBuf::readVarInt,
      FriendlyByteBuf::readResourceLocation
    );
    return new ClientboundArgumentTypeMappingsPacket(map);
  }

  private static void encode(final FriendlyByteBuf buf, final ClientboundArgumentTypeMappingsPacket pkt) {
    buf.writeMap(
      pkt.mappings,
      FriendlyByteBuf::writeVarInt,
      FriendlyByteBuf::writeResourceLocation
    );
  }

  public void sendTo(final PacketSender responder) {
    responder.sendPacket(this);
  }

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
