package me.ayydxn.niagara;

import me.ayydxn.niagara.utils.NativeLibraryLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class NiagaraPreLaunchEntrypoint implements PreLaunchEntrypoint
{
    @Override
    public void onPreLaunch()
    {
        NativeLibraryLoader.load();
    }
}
