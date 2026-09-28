import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

const files = [
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/enderchest.sk", "enderchest.sk"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/craft.sk", "craft.sk"],
  ["C:/Users/MerelyMe/Documents/MerelyMeSMP/fix-rank-perms.sk", "fix-rank-perms.sk"],
  ["C:/Users/MerelyMe/Documents/EconomySMP/plugins/SKCommands/settings.yml", "../SKCommands/settings.yml"],
];

const node = process.execPath;
const gp = "C:/Users/MerelyMe/Documents/MerelyMeSMP/gp-save-file.mjs";
const base = "https://www.g-portal.com/eur/server/minecraft-ram/8699357/files?edit=/plugins/Skript/scripts/";

for (const [local, name] of files) {
  const editUrl = name.startsWith("../")
    ? `https://www.g-portal.com/eur/server/minecraft-ram/8699357/files?edit=/plugins/${name.slice(3)}`
    : base + name;
  console.log("saving", name);
  execFileSync(node, [gp, editUrl, local], { stdio: "inherit" });
}

console.log("Done — run /sk reload and /fixrankperms in console");
