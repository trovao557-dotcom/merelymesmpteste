using System;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

class FixTitles
{
    static void Main()
    {
        Encoding utf8 = new UTF8Encoding(false);
        char shift = (char)0xF804;
        string eco = @"C:\Users\MerelyMe\Documents\EconomySMP\plugins";
        string P(int i) { return "&f" + shift + (char)(0xE050 + i); }

        void Set(string file, string pattern, int idx)
        {
            string path = Path.Combine(eco, file);
            string t = File.ReadAllText(path, utf8);
            string repl = "title: '" + P(idx) + "'";
            string n = Regex.Replace(t, pattern, repl);
            File.WriteAllText(path, n, utf8);
            Console.WriteLine((n != t ? "ok " : "same ") + file);
        }

        void SetMenuTitle(string file, int idx)
        {
            string path = Path.Combine(eco, file);
            string t = File.ReadAllText(path, utf8);
            string repl = "menu_title: '" + P(idx) + "'";
            string n = Regex.Replace(t, @"menu_title:\s*'[^']*'", repl);
            if (n == t) n = Regex.Replace(t, @"^.*$", m => m.Value.Contains("menu_title") || m.Index==0 ? repl : m.Value, RegexOptions.Multiline);
            // first line
            string[] lines = File.ReadAllLines(path, utf8);
            lines[0] = repl;
            File.WriteAllLines(path, lines, utf8);
            Console.WriteLine("ranks " + lines[0].Length);
        }

        Set(@"SKShop\shop.yml", @"title:\s*'[^']*Shop[^']*'", 0);
        Set(@"SKShop\shop.yml", @"    title:\s*'[^']*'", 0);
        Set(@"SKShop\menu-buy.yml", @"title:\s*'[^']*'", 0);
        Set(@"SKAuction\menu-browse.yml", @"title:\s*'[^']*'", 1);
        Set(@"SKAuction\menu-mine.yml", @"title:\s*'[^']*'", 1);
        Set(@"SKAuction\menu-history.yml", @"title:\s*'[^']*'", 1);
        Set(@"SKAuction\menu-confirm.yml", @"title:\s*'[^']*'", 13);
        Set(@"SKOrders\menu-orders.yml", @"title:\s*'[^']*'", 2);
        Set(@"SKOrders\menu-your-orders.yml", @"title:\s*'[^']*'", 2);
        Set(@"SKOrders\menu-select-item.yml", @"title:\s*'[^']*'", 2);
        Set(@"SKOrders\menu-new-order.yml", @"title:\s*'[^']*'", 2);
        Set(@"SKOrders\menu-deliver.yml", @"title:\s*'[^']*'", 2);
        Set(@"SKInfo\settings.yml", @"title:\s*'[^']*'", 3);
        Set(@"SKSell\menu-sell.yml", @"title:\s*'[^']*'", 4);
        Set(@"SKKits\settings.yml", @"title:\s*'[^']*'", 6);
        Set(@"SKSettings\menu-settings.yml", @"title:\s*'[^']*'", 7);
        Set(@"SKBounty\menu-bounties.yml", @"title:\s*'[^']*'", 8);
        Set(@"SKStats\menu-stats.yml", @"title:\s*'[^']*'", 9);
        Set(@"SKRules\menu-rules.yml", @"title:\s*'[^']*'", 10);
        Set(@"SKRtp\menu-rtp.yml", @"title:\s*'[^']*'", 11);
        Set(@"SKCrates\menu-crate.yml", @"title:\s*'[^']*'", 12);
        Set(@"SKTpa\menu-confirm.yml", @"title:\s*'[^']*'", 13);
        Set(@"SKTeam\menu-team.yml", @"title:\s*'[^']*'", 15);
        Set(@"SKHomes\settings.yml", @"title:\s*'[^']*'", 16);
        Set(@"SKPointShop\settings.yml", @"title:\s*'[^']*'", 17);
        SetMenuTitle(@"DeluxeMenus\gui_menus\ranks.yml", 5);

        string ah = Path.Combine(eco, @"SKAuction\menu-browse.yml");
        string aht = File.ReadAllText(ah, utf8);
        aht = aht.Replace("name: '&fC:\\Users\\MerelyMe &#71F521&lMY ITEMS'", "name: '&f" + (char)0xE026 + " &#71F521&lMY ITEMS'");
        File.WriteAllText(ah, aht, utf8);
    }
}
