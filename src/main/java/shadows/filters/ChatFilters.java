package shadows.filters;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.MessageArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(ChatFilters.MODID)
public class ChatFilters {

	public static final String MODID = "chatfilters";
	public static final Logger LOGGER = LogManager.getLogger(MODID);

	public static String[] blocked;
	public static Replacement[] replacements;
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();

	public ChatFilters() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
		bus.addListener(this::setup);
	}

	@SubscribeEvent
	public void setup(FMLCommonSetupEvent e) {
		MinecraftForge.EVENT_BUS.addListener(this::filter);
		MinecraftForge.EVENT_BUS.addListener(this::starting);
		File blockFile = new File(FMLPaths.CONFIGDIR.get().toFile(), "filters/blocked.json");
		File replaceFile = new File(FMLPaths.CONFIGDIR.get().toFile(), "filters/replacements.json");
		try {

			if (!blockFile.exists()) {
				blockFile.getParentFile().mkdirs();
				blockFile.createNewFile();
				String[] def = { "fuck" };
				FileWriter write = new FileWriter(blockFile);
				GSON.toJson(def, write);
				write.close();
			}

			if (!replaceFile.exists()) {
				replaceFile.getParentFile().mkdirs();
				replaceFile.createNewFile();
				Replacement[] def = { new Replacement("bitch", "!@$%$") };
				FileWriter write = new FileWriter(replaceFile);
				GSON.toJson(def, write);
				write.close();
			}

			FileReader reader;

			blocked = GSON.fromJson(reader = new FileReader(blockFile), String[].class);
			reader.close();

			replacements = GSON.fromJson(reader = new FileReader(replaceFile), Replacement[].class);
			reader.close();

		} catch (IOException ex) {
			LOGGER.error("Failed to load config files from disk.");
		}
	}

	@SubscribeEvent
	public void filter(ServerChatEvent e) {
		ITextComponent comp = filterMsg(e.getComponent());
		if (comp != null) {
			e.setComponent(comp);
		} else {
			e.setCanceled(true);
			e.getPlayer().sendMessage(new StringTextComponent("Message contains blocked phrases, it was not delivered.").applyTextStyle(TextFormatting.RED));
		}
	}

	@SubscribeEvent
	public void starting(FMLServerStartedEvent e) {
		FilteredMessageCommand.register(e.getServer().getCommandManager().getDispatcher());
	}

	@Nullable
	private static ITextComponent filterMsg(ITextComponent component) {
		String s = component.getFormattedText();
		for (String b : blocked) {
			if (s.contains(b)) return null;
		}
		for (Replacement r : replacements) {
			s = s.replace(r.getWord(), r.getReplace());
		}
		return new StringTextComponent(s);
	}

	public static class FilteredMessageCommand {
		public static void register(CommandDispatcher<CommandSource> dispatcher) {
			LiteralCommandNode<CommandSource> literalcommandnode = dispatcher.register(Commands.literal("msg").then(Commands.argument("targets", EntityArgument.players()).then(Commands.argument("message", MessageArgument.message()).executes((ctx) -> {
				return sendPrivateMessage(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), MessageArgument.getMessage(ctx, "message"));
			}))));
			dispatcher.register(Commands.literal("tell").redirect(literalcommandnode));
			dispatcher.register(Commands.literal("w").redirect(literalcommandnode));
		}

		private static int sendPrivateMessage(CommandSource source, Collection<ServerPlayerEntity> recipients, ITextComponent message) {
			ITextComponent filtered = filterMsg(message);
			if (filtered != null) {
				for (ServerPlayerEntity serverplayerentity : recipients) {
					serverplayerentity.sendMessage((new TranslationTextComponent("commands.message.display.incoming", source.getDisplayName(), filtered)).applyTextStyles(new TextFormatting[] { TextFormatting.GRAY, TextFormatting.ITALIC }));
					source.sendFeedback((new TranslationTextComponent("commands.message.display.outgoing", serverplayerentity.getDisplayName(), filtered)).applyTextStyles(new TextFormatting[] { TextFormatting.GRAY, TextFormatting.ITALIC }), false);
				}
			} else source.sendErrorMessage(new StringTextComponent("Message contains blocked phrases, it was not delivered."));

			return recipients.size();
		}
	}

}
