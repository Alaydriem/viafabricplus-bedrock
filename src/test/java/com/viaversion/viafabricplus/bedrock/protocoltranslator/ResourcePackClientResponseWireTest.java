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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ResourcePackResponse;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.junit.jupiter.api.Test;

class ResourcePackClientResponseWireTest {

    /**
     * The "downloading" branch as gophertunnel writes it since protocol 2168: varuint branch index
     * 1, the branch name, then the pack ids with a varuint count. ViaBedrock writes the count as a
     * u16 LE, the pre-2168 layout, which a vanilla server cannot read.
     */
    @Test
    void downloadingBranchUsesVarUIntPackCount() {
        final ByteBuf buf = Unpooled.buffer();

        // Written the way ResourcePackLoadStateTracker does, with the pack list type it is patched to use
        BedrockTypes.UNSIGNED_VAR_INT.write(buf, ResourcePackResponse.Downloading.getValue());
        BedrockTypes.STRING.write(buf, "downloading");
        ResourcePackClientResponseWire.DOWNLOADING_PACKS_TYPE.write(buf, new String[]{"abc_1.0.0"});

        // 01 | 0b "downloading" | 01 (varuint count) | 09 "abc_1.0.0"
        assertEquals("01" + "0b" + "646f776e6c6f6164696e67" + "01" + "09" + "6162635f312e302e30", ByteBufUtil.hexDump(buf));
    }

    /** Pinned by a real 1.26.51 client capture: {@code 02 13 "downloadingfinished"}. */
    @Test
    void branchIndexIsZeroBased() {
        assertEquals(2, ResourcePackResponse.DownloadingFinished.getValue());
    }

}
