package org.viridexa.api.plant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlantSuggestionsTest {
 @Test void blankInputDoesNotQuery(){var mapper=mock(PlantMapper.class);assertTrue(new PlantService(mapper).suggestions(" ",6).isEmpty());verifyNoInteractions(mapper);}
 @Test void escapesLiteralWildcardsAndUsesBoundedPrefix(){var mapper=mock(PlantMapper.class);new PlantService(mapper).suggestions(" A_%! ",6);verify(mapper).suggestions("A_%!","A!_!%!!%",6);verify(mapper,never()).countPlants(any());}
 @Test void invalidLimitsAndControlCharactersAreRejected(){var service=new PlantService(mock(PlantMapper.class));assertThrows(IllegalArgumentException.class,()->service.suggestions("银",9));assertThrows(IllegalArgumentException.class,()->service.suggestions("银\n杏",6));assertThrows(IllegalArgumentException.class,()->service.suggestions("x".repeat(81),6));}
}
