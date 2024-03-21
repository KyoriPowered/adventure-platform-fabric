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
package net.kyori.adventure.platform.fabric.impl.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.platform.fabric.FabricAudiences;
import net.kyori.adventure.platform.fabric.impl.AdventureCommon;
import net.kyori.adventure.platform.fabric.impl.ControlledAudience;
import net.kyori.adventure.platform.fabric.impl.FabricAudiencesInternal;
import net.kyori.adventure.platform.fabric.impl.GameEnums;
import net.kyori.adventure.platform.fabric.impl.PointerProviderBridge;
import net.kyori.adventure.platform.fabric.impl.accessor.minecraft.network.ServerGamePacketListenerImplAccess;
import net.kyori.adventure.platform.fabric.impl.accessor.minecraft.world.level.LevelAccess;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.resource.ResourcePackCallback;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.kyori.adventure.util.MonkeyBars;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

public final class ServerPlayerAudience implements ControlledAudience {
  private final ServerPlayer player;
  private final FabricServerAudiencesImpl controller;

  public ServerPlayerAudience(final ServerPlayer player, final FabricServerAudiencesImpl controller) {
    this.player = player;
    this.controller = controller;
  }

  void sendPacket(final Packet<? extends ClientCommonPacketListener> packet) {
    this.player.connection.send(packet);
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // bundle generics don't handle configuration phase
  void sendBundle(final List<? extends Packet<? extends ClientCommonPacketListener>> packet) {
    this.player.connection.send(new ClientboundBundlePacket((List) packet));
  }

  @Override
  public void sendMessage(final @NotNull Component message) {
    this.player.sendSystemMessage(this.controller.toNative(message));
  }

  @Override
  @Deprecated
  public void sendMessage(final Identity source, final Component text, final net.kyori.adventure.audience.MessageType type) {
    final boolean shouldSend = switch (this.player.getChatVisibility()) {
      case FULL -> true;
      case SYSTEM -> type == MessageType.SYSTEM;
      case HIDDEN -> false;
    };

    if (shouldSend) {
      this.player.sendSystemMessage(this.controller.toNative(text));
    }
  }

  private net.minecraft.network.chat.ChatType.Bound toMc(final ChatType.Bound adv) {
    return AdventureCommon.chatTypeToNative(adv, this.controller);
  }

  @Override
  public void sendMessage(final @NotNull Component message, final ChatType.@NotNull Bound boundChatType) {
    final OutgoingChatMessage outgoing = new OutgoingChatMessage.Disguised(
        this.controller.toNative(message)
    );
    this.player.sendChatMessage(outgoing, false, this.toMc(boundChatType));
  }

  @Override
  public void sendMessage(final @NotNull SignedMessage signedMessage, final ChatType.@NotNull Bound boundChatType) {
    if ((Object) signedMessage instanceof PlayerChatMessage pcm) {
      if (pcm.isSystem()) {
        this.player.sendChatMessage(new OutgoingChatMessage.Disguised(pcm.decoratedContent()), false, this.toMc(boundChatType));
      } else {
        this.player.sendChatMessage(new OutgoingChatMessage.Player(pcm), false, this.toMc(boundChatType));
      }
    } else {
      this.sendMessage(Objects.requireNonNullElse(signedMessage.unsignedContent(), Component.text(signedMessage.message())), boundChatType);
    }
  }

  @Override
  public void deleteMessage(final SignedMessage.@NotNull Signature signature) {
    this.sendPacket(new ClientboundDeleteChatPacket(((MessageSignature) signature).pack(((ServerGamePacketListenerImplAccess) this.player.connection).accessor$messageSignatureCache())));
  }

  @Override
  public void sendActionBar(final @NotNull Component message) {
    this.player.sendSystemMessage(this.controller.toNative(message), true);
  }

  @Override
  public void showBossBar(final @NotNull BossBar bar) {
    FabricServerAudiencesImpl.forEachInstance(controller -> {
      if (controller != this.controller) {
        controller.bossBars.unsubscribe(this.player, bar);
      }
    });
    this.controller.bossBars.subscribe(this.player, bar);
  }

  @Override
  public void hideBossBar(final @NotNull BossBar bar) {
    FabricServerAudiencesImpl.forEachInstance(controller -> controller.bossBars.unsubscribe(this.player, bar));
  }

  private long seed(final @NotNull Sound sound) {
    if (sound.seed().isPresent()) {
      return sound.seed().getAsLong();
    } else {
      return ((LevelAccess) this.player.level()).accessor$threadSafeRandom().nextLong();
    }
  }

  private Holder<SoundEvent> eventHolder(final @NotNull Sound sound) {
    final var soundEventRegistry = this.controller.registryAccess()
      .registryOrThrow(Registries.SOUND_EVENT);
    final var soundKey = FabricAudiences.toNative(sound.name());

    final var eventOptional = soundEventRegistry.getHolder(ResourceKey.create(Registries.SOUND_EVENT, soundKey));
    return eventOptional.isPresent() ? eventOptional.get() : Holder.direct(SoundEvent.createVariableRangeEvent(soundKey));
  }

  @Override
  public void playSound(final @NotNull Sound sound) {
    this.playSound(sound, this.player.getX(), this.player.getY(), this.player.getZ());
  }

  @Override
  public void playSound(final @NotNull Sound sound, final double x, final double y, final double z) {
    this.sendPacket(new ClientboundSoundPacket(
      this.eventHolder(sound),
      GameEnums.SOUND_SOURCE.toMinecraft(sound.source()),
      x,
      y,
      z,
      sound.volume(),
      sound.pitch(),
      this.seed(sound)
    ));
  }

  @Override
  public void playSound(final @NotNull Sound sound, final Sound.@NotNull Emitter emitter) {
    final Entity targetEntity;
    if (emitter == Sound.Emitter.self()) {
      targetEntity = this.player;
    } else if (emitter instanceof Entity) {
      targetEntity = (Entity) emitter;
    } else {
      throw new IllegalArgumentException("Provided emitter '" + emitter + "' was not Sound.Emitter.self() or an Entity");
    }

    if (!this.player.level().equals(targetEntity.level())) {
      // don't send unless entities are in the same dimension
      return;
    }

    this.sendPacket(new ClientboundSoundEntityPacket(
      this.eventHolder(sound),
      GameEnums.SOUND_SOURCE.toMinecraft(sound.source()),
      targetEntity,
      sound.volume(),
      sound.pitch(),
      this.seed(sound)
    ));
  }

  @Override
  public void stopSound(final @NotNull SoundStop stop) {
    final @Nullable Key sound = stop.sound();
    final Sound.@Nullable Source src = stop.source();
    final @Nullable SoundSource cat = src == null ? null : GameEnums.SOUND_SOURCE.toMinecraft(src);
    this.sendPacket(new ClientboundStopSoundPacket(sound == null ? null : FabricAudiences.toNative(sound), cat));
  }

  @Override
  public void openBook(final @NotNull Book book) {
    if (book.pages().size() > WrittenBookContent.MAX_PAGES) {
      throw new IllegalArgumentException("Book provided had " + book.pages().size() + " pages, but is only allowed a maximum of " + WrittenBookContent.MAX_PAGES);
    }
    final WrittenBookContent content = new WrittenBookContent(
      Filterable.passThrough(validateField(this.adventure$plain(book.title()), WrittenBookContent.TITLE_MAX_LENGTH, "title")),
      this.adventure$plain(book.author()),
      0,
      book.pages().stream()
        .map(this.controller::toNative)
        .map(Filterable::passThrough)
        .toList(),
      true
    );

    final ItemStack bookStack = new ItemStack(Items.WRITTEN_BOOK, 1);
    bookStack.applyComponents(
      DataComponentPatch.builder()
      .set(DataComponents.WRITTEN_BOOK_CONTENT, content)
      .build()
    );

    final ItemStack previous = this.player.getInventory().getSelected();
    this.sendPacket(new ClientboundContainerSetSlotPacket(-2, this.player.containerMenu.getStateId(), this.player.getInventory().selected, bookStack));
    this.player.openItemGui(bookStack, InteractionHand.MAIN_HAND);
    this.sendPacket(new ClientboundContainerSetSlotPacket(-2, this.player.containerMenu.getStateId(), this.player.getInventory().selected, previous));
  }

  private static String validateField(final String content, final int length, final String name) {
    if (content == null) {
      return content;
    }

    final int actual = content.length();
    if (actual > length) {
      throw new IllegalArgumentException("Field '" + name + "' has a maximum length of " + length + " but was passed '" + content + "', which was " + actual + " characters long.");
    }
    return content;
  }

  private String adventure$plain(final @NotNull Component component) {
    return PlainTextComponentSerializer.plainText().serialize(this.controller.renderer().render(component, this));
  }

  @Override
  public void showTitle(final @NotNull Title title) {
    if (title.subtitle() != Component.empty()) {
      this.sendPacket(new ClientboundSetSubtitleTextPacket(this.controller.toNative(title.subtitle())));
    }

    final Title.@Nullable Times times = title.times();
    if (times != null) {
      final int fadeIn = ticks(times.fadeIn());
      final int fadeOut = ticks(times.fadeOut());
      final int dwell = ticks(times.stay());
      if (fadeIn != -1 || fadeOut != -1 || dwell != -1) {
        this.sendPacket(new ClientboundSetTitlesAnimationPacket(fadeIn, dwell, fadeOut));
      }
    }

    if (title.title() != Component.empty()) {
      this.sendPacket(new ClientboundSetTitleTextPacket(this.controller.toNative(title.title())));
    }
  }

  @Override
  public <T> void sendTitlePart(final @NotNull TitlePart<T> part, final @NotNull T value) {
    Objects.requireNonNull(value, "value");
    if (part == TitlePart.TITLE) {
      this.sendPacket(new ClientboundSetTitleTextPacket(this.controller.toNative((Component) value)));
    } else if (part == TitlePart.SUBTITLE) {
      this.sendPacket(new ClientboundSetSubtitleTextPacket(this.controller.toNative((Component) value)));
    } else if (part == TitlePart.TIMES) {
      final Title.Times times = (Title.Times) value;
      final int fadeIn = ticks(times.fadeIn());
      final int fadeOut = ticks(times.fadeOut());
      final int dwell = ticks(times.stay());
      if (fadeIn != -1 || fadeOut != -1 || dwell != -1) {
        this.sendPacket(new ClientboundSetTitlesAnimationPacket(fadeIn, dwell, fadeOut));
      }
    } else {
      throw new IllegalArgumentException("Unknown TitlePart '" + part + "'");
    }
  }

  static int ticks(final @NotNull Duration duration) {
    return duration.getSeconds() == -1 ? -1 : (int) (duration.toMillis() / 50);
  }

  @Override
  public void clearTitle() {
    this.sendPacket(new ClientboundClearTitlesPacket(false));
  }

  @Override
  public void resetTitle() {
    this.sendPacket(new ClientboundClearTitlesPacket(true));
  }

  @Override
  public void sendPlayerListHeader(final @NotNull Component header) {
    requireNonNull(header, "header");
    ((ServerPlayerBridge) this.player).bridge$updateTabList(this.controller.toNative(header), null);
  }

  @Override
  public void sendPlayerListFooter(final @NotNull Component footer) {
    requireNonNull(footer, "footer");
    ((ServerPlayerBridge) this.player).bridge$updateTabList(null, this.controller.toNative(footer));

  }

  @Override
  public void sendPlayerListHeaderAndFooter(final @NotNull Component header, final @NotNull Component footer) {
    ((ServerPlayerBridge) this.player).bridge$updateTabList(
      this.controller.toNative(requireNonNull(header, "header")),
      this.controller.toNative(requireNonNull(footer, "footer")));
  }

  @Override
  public void sendResourcePacks(final @NotNull ResourcePackRequest request) {
    final List<Packet<ClientCommonPacketListener>> packets = new ArrayList<>(request.packs().size());
    if (request.replace()) {
      packets.add(new ClientboundResourcePackPopPacket(Optional.empty()));
    }

    final net.minecraft.network.chat.@Nullable Component prompt = this.toNativeNullable(request.prompt());
    for (final Iterator<ResourcePackInfo> it = request.packs().iterator(); it.hasNext();) {
      final ResourcePackInfo pack = it.next();
      packets.add(new ClientboundResourcePackPushPacket(
        pack.id(),
        pack.uri().toASCIIString(),
        pack.hash(),
        request.required(),
        it.hasNext() ? Optional.empty() : Optional.of(prompt)
      ));

      if (request.callback() != ResourcePackCallback.noOp()) {
        ((ServerCommonPacketListenerImplBridge) this.player.connection).adventure$bridge$registerPackCallback(pack.id(), this.controller, request.callback());
      }
    }

    this.sendBundle(packets);
  }

  @Override
  public void removeResourcePacks(final @NotNull UUID id, final @NotNull UUID@NotNull... others) {
    this.sendBundle(
      MonkeyBars.nonEmptyArrayToList(pack -> new ClientboundResourcePackPopPacket(Optional.of(pack)), id, others)
    );
  }

  @Override
  public void clearResourcePacks() {
    this.sendPacket(new ClientboundResourcePackPopPacket(Optional.empty()));
  }

  private net.minecraft.network.chat.@Nullable Component toNativeNullable(final @Nullable Component comp) {
    return comp == null ? null : this.controller.toNative(comp);
  }

  @Override
  public @NotNull Pointers pointers() {
    return ((PointerProviderBridge) this.player).adventure$pointers();
  }

  @Override
  public @NotNull FabricAudiencesInternal controller() {
    return this.controller;
  }
}
