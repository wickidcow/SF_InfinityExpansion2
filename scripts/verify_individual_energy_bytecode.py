#!/usr/bin/env python3
"""Verify actual JVM energy overload references in the packaged individual machines."""
import struct
import sys
import zipfile

ROOT = 'net/guizhanss/infinityexpansion2/'
READ = ('getChargeLong', '(Lorg/bukkit/Location;)J')
DEBIT = ('removeCharge', '(Lorg/bukkit/Location;J)V')
HUD = ('read', '(Lio/github/thebusybiscuit/slimefun4/core/attributes/EnergyNetComponent;Lorg/bukkit/Location;)I')
TARGETS = {
    'implementation/items/machines/AdvancedAnvil': {READ, DEBIT},
    'implementation/items/machines/PoweredBedrock$1': {READ, DEBIT},
    'implementation/items/mobsim/MobSimulationChamber': {READ, DEBIT},
    'implementation/items/machines/SingularityConstructor$Companion': {HUD},
    'implementation/items/machines/VoidHarvester$Companion': {HUD},
    'implementation/items/mobsim/MobSimulationChamber$Companion': {HUD},
    'utils/LegacyHudEnergy': {READ},
}


def references(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Not a JVM class')
    count = struct.unpack_from('>H', data, 8)[0]
    cp = [None] * count
    position, i = 10, 1
    while i < count:
        tag = data[position]
        position += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, position)[0]
            position += 2
            cp[i] = data[position:position + size].decode('utf-8', errors='replace')
            position += size
        elif tag in (3, 4):
            position += 4
        elif tag in (5, 6):
            position += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = (tag, struct.unpack_from('>H', data, position)[0])
            position += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            cp[i] = (tag, *struct.unpack_from('>HH', data, position))
            position += 4
        elif tag == 15:
            position += 3
        else:
            raise ValueError(f'Unexpected constant pool tag: {tag}')
        i += 1
    result = set()
    for entry in cp:
        if isinstance(entry, tuple) and entry[0] in (10, 11):
            _, _, name_type = entry
            _, name, descriptor = cp[name_type]
            result.add((cp[name], cp[descriptor]))
    return result


def main(path):
    with zipfile.ZipFile(path) as jar:
        for relative, required in TARGETS.items():
            calls = references(jar.read(ROOT + relative + '.class'))
            forbidden = {call for call in calls if call[0] == 'getCharge'
                         or call == ('removeCharge', '(Lorg/bukkit/Location;I)V')}
            if forbidden or not required.issubset(calls):
                raise AssertionError(f'{relative}: deprecated={forbidden}, missing={required - calls}')
    print('Verified all nine migrated call sites: long energy overloads and signed-int HUD bridge.')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: verify_individual_energy_bytecode.py <shaded-jar>')
    main(sys.argv[1])
