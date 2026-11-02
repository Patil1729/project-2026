package com.party.user.picture;

import com.party.administration.access.service.AreaAccessService;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.entity.AppUser;
import com.party.user.picture.service.ProfilePictureService;
import com.party.user.picture.storage.*;
import com.party.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfilePictureTest {
    @TempDir Path root;
    final ProfileImageNormalizer images = new ProfileImageNormalizer();
    static MockMultipartFile image(String format, int width, int height) throws Exception {
        var buffer = new ByteArrayOutputStream();
        var image = new BufferedImage(width,height,format.equals("jpg")?BufferedImage.TYPE_INT_RGB:BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image,format,buffer);image.flush();
        return new MockMultipartFile("file","picture."+format,"image/"+format,buffer.toByteArray());
    }
    @Test void pngIsResizedAndReencoded() throws Exception {var output=images.normalize(image("png",1800,1200),2097152);assertEquals(512,output.width());assertTrue(output.height()<=512);assertNotNull(ImageIO.read(new ByteArrayInputStream(output.bytes())));}
    @Test void jpegBecomesPng() throws Exception {var output=images.normalize(image("jpg",250,150),2097152);assertEquals(250,output.width());assertEquals((byte)137,output.bytes()[0]);}
    @Test void forgedMimeIsRejected(){assertThrows(RegistrationException.class,()->images.normalize(new MockMultipartFile("file","p.png","image/png","<svg>bad</svg>".getBytes()),2097152));}
    @Test void oversizedFilesAreRejected() throws Exception {var file=image("png",100,100);assertEquals("PROFILE_PICTURE_TOO_LARGE",assertThrows(RegistrationException.class,()->images.normalize(file,1)).getErrorCode());}
    @Test void excessiveDimensionsAreRejectedBeforeDecoding() throws Exception {assertEquals("PROFILE_PICTURE_DIMENSIONS",assertThrows(RegistrationException.class,()->images.normalize(image("png",12001,1),2097152)).getErrorCode());}
    @Test void emptyFileIsRejected(){assertThrows(RegistrationException.class,()->images.normalize(new MockMultipartFile("file",new byte[0]),2097152));}
    @Test void ownerCanUploadAndReadMetadataWithoutExposingStorageKey() throws Exception {
        var f=fixture();var response=f.service.upload(10L,image("png",80,80));assertTrue(response.hasProfilePicture());assertEquals(64,response.profilePictureVersion().length());
        try(var content=f.service.content(10L).input()){assertNotNull(ImageIO.read(content));}
        verify(f.access).requireReadUser(10L);
    }
    @Test void otherUserCannotUploadAndScopedReaderCannotMutate() throws Exception {
        var f=fixture();when(f.access.actorId()).thenReturn(20L);
        assertEquals("PROFILE_PICTURE_ACCESS_DENIED",assertThrows(RegistrationException.class,()->f.service.upload(10L,image("png",80,80))).getErrorCode());
        assertThrows(RegistrationException.class,()->f.service.remove(10L));verifyNoInteractions(f.users);
    }
    @Test void readDenialIsCheckedBeforeStorageOrUserLookup() throws Exception {
        var f=fixture();doThrow(new RegistrationException(org.springframework.http.HttpStatus.FORBIDDEN,"AREA_ACCESS_DENIED","Denied")).when(f.access).requireReadUser(10L);
        assertThrows(RegistrationException.class,()->f.service.content(10L));verifyNoInteractions(f.users);
    }
    @Test void replacementDeletesOldFileOnlyAfterCommit() throws Exception {
        var f=fixture();f.service.upload(10L,image("png",80,80));String old=f.user.getProfilePictureKey();
        TransactionSynchronizationManager.initSynchronization();
        try {f.service.upload(10L,image("png",90,90));String created=f.user.getProfilePictureKey();assertTrue(Files.exists(root.resolve(old)));
            for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCommit();
            assertFalse(Files.exists(root.resolve(old)));assertTrue(Files.exists(root.resolve(created)));
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void rollbackRemovesNewFileAndPreservesOldFile() throws Exception {
        var f=fixture();f.service.upload(10L,image("png",80,80));String old=f.user.getProfilePictureKey();TransactionSynchronizationManager.initSynchronization();
        try {f.service.upload(10L,image("png",90,90));String created=f.user.getProfilePictureKey();for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);assertTrue(Files.exists(root.resolve(old)));assertFalse(Files.exists(root.resolve(created)));}
        finally {TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void failedDatabaseWriteRemovesNewFile() throws Exception {
        var f=fixture();when(f.users.saveAndFlush(any())).thenThrow(new IllegalStateException("Test persistence failure"));assertThrows(IllegalStateException.class,()->f.service.upload(10L,image("png",80,80)));
        try(var files=Files.walk(root)){assertEquals(0,files.filter(Files::isRegularFile).count());}
    }
    record Fixture(ProfilePictureService service,UserRepository users,AreaAccessService access,AppUser user){}
    Fixture fixture() throws Exception {
        var users=mock(UserRepository.class);var access=mock(AreaAccessService.class);var user=AppUser.builder().id(10L).enabled(true).build();
        when(access.actorId()).thenReturn(10L);when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(user));when(users.findById(10L)).thenReturn(Optional.of(user));when(users.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        return new Fixture(new ProfilePictureService(users,access,new ProfilePictureStorage(root.toString(),2097152),images,Clock.systemUTC()),users,access,user);
    }
}
