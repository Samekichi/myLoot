package org.spoorn.myloot.client;

import lombok.extern.log4j.Log4j2;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spoorn.myloot.client.model.MyLootModelResourceProvider;

@Log4j2
@Environment(EnvType.CLIENT)
public class MyLootClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        log.info("Hello client from myLoot!");
        
        // Barrel custom model
        MyLootModelResourceProvider.init();
    }
}
