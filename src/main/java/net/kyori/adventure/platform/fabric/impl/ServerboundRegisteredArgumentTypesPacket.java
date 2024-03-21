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

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * A packet sent client to server, to let the server know which optional argument types are available on the server.
 *
 * <p>This packet is sent by players on join, before the command tree is sent to the client.</p>
 *
 * @param known Known argument type ids
 */
public record ServerboundRegisteredArgumentTypesPacket(Set<ResourceLocation> known) implements CustomPacketPayload {
  public static final CustomPacketPayload.Type<ServerboundRegisteredArgumentTypesPacket> TYPE = new CustomPacketPayload.Type<>(AdventureCommon.res("registered_args"));
  private static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRegisteredArgumentTypesPacket> CODEC = StreamCodec.of(
    ServerboundRegisteredArgumentTypesPacket::encode,
    ServerboundRegisteredArgumentTypesPacket::of
  );

  public static void register() {
    PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
    ServerPlayNetworking.registerGlobalReceiver(TYPE, (pkt, ctx) -> {
      ctx.player().getServer().execute(() -> { // on main thread
        ServerArgumentTypes.knownArgumentTypes(ctx.player(), pkt.known, ctx.responseSender());
      });
    });
  }

  public static ServerboundRegisteredArgumentTypesPacket of(final Set<ResourceLocation> idents) {
    return new ServerboundRegisteredArgumentTypesPacket(Set.copyOf(idents));
  }

  public static ServerboundRegisteredArgumentTypesPacket of(final @NotNull FriendlyByteBuf buf) {
    final int length = buf.readVarInt();
    final Set<ResourceLocation> items = new HashSet<>();
    for (int i = 0; i < length; ++i) {
      items.add(buf.readResourceLocation());
    }
    return of(items);
  }

  private static void encode(final FriendlyByteBuf buffer, final ServerboundRegisteredArgumentTypesPacket pkt) {
    buffer.writeVarInt(pkt.known.size());
    for (final ResourceLocation id : pkt.known) {
      buffer.writeResourceLocation(id);
    }
  }

  /**
   * Send the client's list of identifiers to the server.
   *
   * @param sender the sender to send the packet to
   */
  public void sendTo(final PacketSender sender) {
    sender.sendPacket(this);
  }

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
