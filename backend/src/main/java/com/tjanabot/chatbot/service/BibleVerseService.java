package com.tjanabot.chatbot.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for suggesting Bible verses based on website topics and themes
 */
@Service
public class BibleVerseService {

    private static final Map<String, String> TOPIC_VERSES = new HashMap<>();

    static {
        // Business & Work
        TOPIC_VERSES.put("business", "Colossians 3:23 - 'Whatever you do, work at it with all your heart, as working for the Lord, not for human masters.'");
        TOPIC_VERSES.put("work", "Proverbs 16:3 - 'Commit to the Lord whatever you do, and he will establish your plans.'");
        TOPIC_VERSES.put("career", "Proverbs 22:29 - 'Do you see someone skilled in their work? They will serve before kings; they will not serve before officials of low rank.'");

        // Healthcare & Medical
        TOPIC_VERSES.put("health", "3 John 1:2 - 'Dear friend, I pray that you may enjoy good health and that all may go well with you, even as your soul is getting along well.'");
        TOPIC_VERSES.put("medical", "Jeremiah 17:14 - 'Heal me, Lord, and I will be healed; save me and I will be saved, for you are the one I praise.'");
        TOPIC_VERSES.put("hospital", "Isaiah 41:10 - 'So do not fear, for I am with you; do not be dismayed, for I am your God. I will strengthen you and help you.'");

        // Education & Learning
        TOPIC_VERSES.put("education", "Proverbs 1:5 - 'Let the wise listen and add to their learning, and let the discerning get guidance.'");
        TOPIC_VERSES.put("school", "Proverbs 4:13 - 'Hold on to instruction, do not let it go; guard it well, for it is your life.'");
        TOPIC_VERSES.put("learning", "Proverbs 9:9 - 'Instruct the wise and they will be wiser still; teach the righteous and they will add to their learning.'");

        // Technology & Innovation
        TOPIC_VERSES.put("technology", "Romans 12:2 - 'Do not conform to the pattern of this world, but be transformed by the renewing of your mind.'");
        TOPIC_VERSES.put("innovation", "Ecclesiastes 3:11 - 'He has made everything beautiful in its time.'");
        TOPIC_VERSES.put("software", "Proverbs 2:6 - 'For the Lord gives wisdom; from his mouth come knowledge and understanding.'");

        // Service & Hospitality
        TOPIC_VERSES.put("service", "1 Peter 4:10 - 'Each of you should use whatever gift you have received to serve others, as faithful stewards of God's grace in its various forms.'");
        TOPIC_VERSES.put("hospitality", "Romans 12:13 - 'Share with the Lord's people who are in need. Practice hospitality.'");
        TOPIC_VERSES.put("restaurant", "Acts 20:35 - 'It is more blessed to give than to receive.'");

        // Finance & Banking
        TOPIC_VERSES.put("finance", "Proverbs 3:9-10 - 'Honor the Lord with your wealth, with the firstfruits of all your crops; then your barns will be filled to overflowing.'");
        TOPIC_VERSES.put("banking", "Luke 16:10 - 'Whoever can be trusted with very little can also be trusted with much.'");
        TOPIC_VERSES.put("investment", "Matthew 6:20 - 'But store up for yourselves treasures in heaven, where moths and vermin do not destroy, and where thieves do not break in and steal.'");

        // Real Estate & Construction
        TOPIC_VERSES.put("realestate", "Proverbs 24:3-4 - 'By wisdom a house is built, and through understanding it is established; through knowledge its rooms are filled with rare and beautiful treasures.'");
        TOPIC_VERSES.put("construction", "Psalm 127:1 - 'Unless the Lord builds the house, the builders labor in vain.'");
        TOPIC_VERSES.put("home", "Joshua 24:15 - 'But as for me and my household, we will serve the Lord.'");

        // Legal & Justice
        TOPIC_VERSES.put("legal", "Micah 6:8 - 'He has shown you, O mortal, what is good. And what does the Lord require of you? To act justly and to love mercy and to walk humbly with your God.'");
        TOPIC_VERSES.put("law", "Proverbs 21:15 - 'When justice is done, it brings joy to the righteous but terror to evildoers.'");
        TOPIC_VERSES.put("justice", "Isaiah 1:17 - 'Learn to do right; seek justice. Defend the oppressed.'");

        // Creative & Arts
        TOPIC_VERSES.put("creative", "Exodus 35:35 - 'He has filled them with skill to do all kinds of work as engravers, designers, embroiderers in blue, purple and scarlet yarn and fine linen, and weavers—all of them skilled workers and designers.'");
        TOPIC_VERSES.put("design", "Psalm 90:17 - 'May the favor of the Lord our God rest on us; establish the work of our hands for us—yes, establish the work of our hands.'");
        TOPIC_VERSES.put("art", "Colossians 3:17 - 'And whatever you do, whether in word or deed, do it all in the name of the Lord Jesus.'");

        // Travel & Tourism
        TOPIC_VERSES.put("travel", "Psalm 121:8 - 'The Lord will watch over your coming and going both now and forevermore.'");
        TOPIC_VERSES.put("tourism", "Proverbs 3:5-6 - 'Trust in the Lord with all your heart and lean not on your own understanding; in all your ways submit to him, and he will make your paths straight.'");

        // Non-profit & Ministry
        TOPIC_VERSES.put("nonprofit", "Matthew 25:40 - 'Truly I tell you, whatever you did for one of the least of these brothers and sisters of mine, you did for me.'");
        TOPIC_VERSES.put("charity", "2 Corinthians 9:7 - 'Each of you should give what you have decided in your heart to give, not reluctantly or under compulsion, for God loves a cheerful giver.'");
        TOPIC_VERSES.put("ministry", "Matthew 28:19-20 - 'Therefore go and make disciples of all nations, baptizing them in the name of the Father and of the Son and of the Holy Spirit.'");

        // Fitness & Wellness
        TOPIC_VERSES.put("fitness", "1 Corinthians 6:19-20 - 'Do you not know that your bodies are temples of the Holy Spirit, who is in you, whom you have received from God? You are not your own.'");
        TOPIC_VERSES.put("wellness", "1 Timothy 4:8 - 'For physical training is of some value, but godliness has value for all things, holding promise for both the present life and the life to come.'");

        // Family & Children
        TOPIC_VERSES.put("family", "Ephesians 6:4 - 'Fathers, do not exasperate your children; instead, bring them up in the training and instruction of the Lord.'");
        TOPIC_VERSES.put("children", "Proverbs 22:6 - 'Start children off on the way they should go, and even when they are old they will not turn from it.'");
        TOPIC_VERSES.put("parenting", "Deuteronomy 6:7 - 'Impress them on your children. Talk about them when you sit at home and when you walk along the road, when you lie down and when you get up.'");

        // General/Default
        TOPIC_VERSES.put("default", "Philippians 4:13 - 'I can do all this through him who gives me strength.'");
        TOPIC_VERSES.put("general", "Matthew 5:16 - 'In the same way, let your light shine before others, that they may see your good deeds and glorify your Father in heaven.'");
    }

    /**
     * Suggest a Bible verse based on website description and content
     */
    public String suggestBibleVerse(String websiteUrl, String description, String websiteContent) {
        String combinedText = (description != null ? description.toLowerCase() : "") + " " +
                             (websiteContent != null ? websiteContent.toLowerCase() : "") + " " +
                             (websiteUrl != null ? websiteUrl.toLowerCase() : "");

        // Check highly specific keyword patterns first (to override "service" substring matches)
        if (containsAny(combinedText, "church", "worship", "faith", "prayer")) {
            return "Hebrews 10:25 - 'Not giving up meeting together, as some are in the habit of doing, but encouraging one another—and all the more as you see the Day approaching.'";
        }

        if (containsAny(combinedText, "beauty", "salon", "spa")) {
            return "1 Peter 3:3-4 - 'Your beauty should not come from outward adornment, such as elaborate hairstyles and the wearing of gold jewelry or fine clothes. Rather, it should be that of your inner self, the unfading beauty of a gentle and quiet spirit.'";
        }

        // Try to find the most relevant verse based on keywords from TOPIC_VERSES
        for (Map.Entry<String, String> entry : TOPIC_VERSES.entrySet()) {
            if (combinedText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Additional keyword matching for common variations (checked after TOPIC_VERSES)
        if (containsAny(combinedText, "shop", "store", "retail", "ecommerce", "commerce")) {
            return "Proverbs 11:1 - 'The Lord detests dishonest scales, but accurate weights find favor with him.'";
        }

        if (containsAny(combinedText, "care", "support", "assist")) {
            return "Galatians 6:2 - 'Carry each other's burdens, and in this way you will fulfill the law of Christ.'";
        }

        if (containsAny(combinedText, "food", "cooking", "recipe", "kitchen")) {
            return "Matthew 4:4 - 'Man shall not live on bread alone, but on every word that comes from the mouth of God.'";
        }

        // Return default verse if no specific match found
        return TOPIC_VERSES.get("general");
    }

    /**
     * Helper method to check if text contains any of the given keywords
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all available topic categories
     */
    public Map<String, String> getAllTopicVerses() {
        return new HashMap<>(TOPIC_VERSES);
    }
}
