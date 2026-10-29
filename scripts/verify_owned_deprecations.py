#!/usr/bin/env python3
"""Allow only exact retained ABI descriptors and Paper's required damage-modifier type."""
import re
import sys
from pathlib import Path

CONFIG='me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config'
BRIDGES={'EnergyGenerator.kt':2,'PoweredBedrock.kt':1,'NetherStarReactor.kt':1,'StorageUnit.kt':1,
         'InfinityReactor.kt':1,'InfinitySingularityReactor.kt':1}
KOTLIN=re.compile(r'^w: .*?/src/main/kotlin/(.+?\.kt):(\d+):\d+ (.+)$')
JAVA=re.compile(r'^.*?/src/main/java/(.+?\.java):(\d+): warning: \[(deprecation|removal)\] (.+)$')


def verify(log):
    found=set()
    for line in Path(log).read_text(encoding='utf-8').splitlines():
        k=KOTLIN.match(line)
        if k and 'deprecat' in k[3].lower():
            path,row,reason=k.groups(); row=int(row)
            source=(Path('src/main/kotlin')/path).read_text().splitlines()
            name=Path(path).name
            valid=(name in BRIDGES and row>1 and source[row-2].strip().startswith('@Deprecated(')
                   and source[row-1].strip().startswith('override fun ') and CONFIG in source[row-1]
                   and reason=="'class Config : Any' is deprecated. Deprecated in Java.")
            if not valid:raise AssertionError('Unexpected Kotlin deprecation: '+line)
            found.add((name,row))
            continue
        j=JAVA.match(line)
        if j:
            path,row,kind,reason=j.groups(); row=int(row)
            source=(Path('src/main/java')/path).read_text().splitlines()
            if not (path=='net/guizhanss/infinityexpansion2/utils/events/BowDamageEvents.java'
                    and kind=='deprecation' and 'DamageModifier' in reason
                    and source[row-1].strip()=='var base = EntityDamageEvent.DamageModifier.BASE;'):
                raise AssertionError('Unexpected Java deprecation/removal: '+line)
            found.add(('BowDamageEvents.java',row))
            continue
        if '/src/main/' in line and ('deprecated' in line.lower() or 'removal' in line.lower()):
            raise AssertionError('Unparsed production diagnostic: '+line)
    for name,maximum in {**BRIDGES,'BowDamageEvents.java':1}.items():
        if sum(f==name for f,_ in found)>maximum:raise AssertionError('Additional compatibility warnings: '+name)
    for path in Path('src/main').rglob('*'):
        if path.suffix in ('.kt','.java') and re.search(r'@(?:file:)?Suppress(?:Warnings)?\([^\n]*(?:deprecation|removal)',path.read_text(),re.I):
            raise AssertionError('Production deprecation suppression returned: '+str(path))
    print(f'{len(found)} explicit compatibility diagnostics; no suppressed/actionable own-source deprecations.')
    return found

if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('Usage: verify_owned_deprecations.py <compile-log>')
    verify(sys.argv[1])
