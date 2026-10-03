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

package com.viaversion.viafabricplus.bedrock.protocoltranslator;

import com.viaversion.viaversion.api.type.Type;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/** The ResourcePackClientResponse wire as it is since protocol 2168, where ViaBedrock still differs from it. */
public final class ResourcePackClientResponseWire {

    /**
     * The pack ids of the "downloading" branch carry a varuint count. ViaBedrock kept the u16 LE count
     * of the pre-2168 status-byte layout, which a vanilla server rejects as malformed.
     */
    public static final Type<String[]> DOWNLOADING_PACKS_TYPE = BedrockTypes.STRING_ARRAY;

    private ResourcePackClientResponseWire() {
    }

}
