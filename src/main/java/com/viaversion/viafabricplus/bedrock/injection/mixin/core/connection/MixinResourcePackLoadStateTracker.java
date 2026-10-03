/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.connection;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.ResourcePackClientResponseWire;
import com.viaversion.viaversion.api.type.Type;
import net.raphimc.viabedrock.protocol.storage.ResourcePackLoadStateTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ResourcePackLoadStateTracker.class, remap = false)
public abstract class MixinResourcePackLoadStateTracker {

    /**
     * The "downloading" ResourcePackClientResponse is written in a lambda inside
     * loadRequestedResourcePacks, hence the wildcard; it is the only read of this type in the class.
     */
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC,
        target = "Lnet/raphimc/viabedrock/protocol/types/BedrockTypes;SHORT_LE_STRING_ARRAY:Lcom/viaversion/viaversion/api/type/Type;"))
    private Type<String[]> useVarIntPackCount(final Type<String[]> original) {
        return ResourcePackClientResponseWire.DOWNLOADING_PACKS_TYPE;
    }

}
