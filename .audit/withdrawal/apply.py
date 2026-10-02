from pathlib import Path
import hashlib, shutil

path = Path('src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageUnit.kt')
blob = lambda data: hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert blob(path.read_bytes()) == 'db24976c85df6898277f09198be42372b129d9e9'
text = path.read_text()
old = '''                val itemToGive = cache.itemStack!!.clone()
                val amount = itemToGive.maxStackSize.coerceAtMost(cache.amount)
                val item = itemToGive.edit { amount(amount) }
                val leftover = pInv.addItem(item)

                if (leftover.isEmpty()) {
                    cache.amount -= amount
                    menu.updateDisplay(cache)
                    menu.location.save(cache)
                }'''
new = '''                val amount = cache.itemStack!!.maxStackSize.coerceAtMost(cache.amount)
                if (StorageWithdrawal.transfer(pInv, cache, amount) > 0) {
                    menu.updateDisplay(cache)
                    menu.location.save(cache)
                }'''
assert text.count(old) == 1
text = text.replace(old, new)
old = '''                    val item = itemToGive.edit { amount(amount) }
                    val leftover = pInv.addItem(item)
                    if (leftover.isEmpty()) {
                        cache.amount -= amount
                    } else {
                        // Inventory is full
                        break
                    }'''
new = '''                    val accepted = StorageWithdrawal.transfer(pInv, cache, amount)
                    if (accepted < amount) {
                        // Account for partial insertion before stopping at the full inventory.
                        break
                    }'''
assert text.count(old) == 1
path.write_text(text.replace(old, new))
assert blob(path.read_bytes()) == '318e9e2f7e557a1a469248214b5e2651f7429b66'
for name, root in [('StorageWithdrawal.java', 'main'), ('StorageWithdrawalTest.java', 'test')]:
    target = Path('src') / root / 'java/net/guizhanss/infinityexpansion2/implementation/items/storage' / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(Path('.audit/withdrawal', name), target)
