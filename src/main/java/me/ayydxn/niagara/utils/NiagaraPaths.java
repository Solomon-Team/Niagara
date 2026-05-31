package me.ayydxn.niagara.utils;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class NiagaraPaths
{
    public static final Path NIAGARA_DIRECTORY = FabricLoader.getInstance().getGameDir().resolve("niagara");
    public static final Path NIAGARA_RESOURCES_DIRECTORY = NIAGARA_DIRECTORY.resolve("resources");
    public static final Path NIAGARA_CACHE_DIRECTORY = NIAGARA_DIRECTORY.resolve("cache");
}
