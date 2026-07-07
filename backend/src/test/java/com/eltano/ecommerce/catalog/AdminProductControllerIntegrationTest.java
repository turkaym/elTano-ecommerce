package com.eltano.ecommerce.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(properties = {
        "app.catalog.seed-on-empty=false",
        "app.uploads.product-images.directory=target/test-product-uploads",
        "app.uploads.product-images.public-path=/uploads/product-images",
        "app.uploads.product-images.max-size-bytes=32"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void createRejectsInvalidImageUrlWith422FieldErrors() throws Exception {
        Category category = createCategory();
        ObjectNode payload = validPayload(category.getId());
        payload.putArray("images")
                .add(image("not-a-url", 0, true));

        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toPrettyString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value("Product images validation failed"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("images[0].url"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Image URL must be a valid http/https URL"));
    }

    @Test
    void createRejectsDuplicateSortOrderWith422FieldErrors() throws Exception {
        Category category = createCategory();

        ObjectNode payload = validPayload(category.getId());
        ArrayNode images = payload.putArray("images");
        images.add(image("https://cdn.example.com/one.jpg", 0, true));
        images.add(image("https://cdn.example.com/two.jpg", 0, false));

        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toPrettyString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("images[1].sortOrder"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Image sortOrder values must be unique per product"));
    }

    @Test
    void createAllowsProductWithoutImages() throws Exception {
        Category category = createCategory();
        ObjectNode payload = validPayload(category.getId());
        payload.putArray("images");

        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toPrettyString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images.length()").value(0));
    }

    @Test
    void createAllowsProductWhenImagesAreOmitted() throws Exception {
        Category category = createCategory();
        ObjectNode payload = validPayload(category.getId());
        payload.remove("images");

        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toPrettyString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images.length()").value(0));
    }

    @Test
    void uploadProductImageStoresImageAndReturnsPublicUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "nuez premium.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[] { 1, 2, 3, 4, 5 });

        String publicUrl = mockMvc.perform(multipart("/api/admin/uploads/product-images")
                .file(file)
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.matchesRegex("/uploads/product-images/[a-f0-9\\-]+-nuez-premium\\.png")))
                .andExpect(jsonPath("$.contentType").value(MediaType.IMAGE_PNG_VALUE))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String url = objectMapper.readTree(publicUrl).get("url").asText();
        mockMvc.perform(get(url))
                .andExpect(status().isOk());
    }

    @Test
    void createAcceptsUrlReturnedByProductImageUpload() throws Exception {
        Category category = createCategory();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "almendra.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[] { 1, 2, 3, 4, 5 });

        String uploadResponse = mockMvc.perform(multipart("/api/admin/uploads/product-images")
                .file(file)
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String uploadedUrl = objectMapper.readTree(uploadResponse).get("url").asText();

        ObjectNode payload = validPayload(category.getId());
        payload.putArray("images")
                .add(image(uploadedUrl, 0, true));

        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toPrettyString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images[0].url").value(uploadedUrl));
    }

    @Test
    void createRejectsRelativeImageUrlOutsideProductUploads() throws Exception {
        Category category = createCategory();
        ObjectNode payload = validPayload(category.getId());
        payload.putArray("images")
                .add(image("/admin/secret.png", 0, true));

        mockMvc.perform(post("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toPrettyString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("images[0].url"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Image URL must be a valid http/https URL"));
    }

    @Test
    void uploadProductImageRejectsUnsupportedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "not-image.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/uploads/product-images")
                .file(file)
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Product image must be a JPG, PNG, or WebP file"));
    }

    @Test
    void uploadProductImageRejectsTooLargeFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.webp",
                "image/webp",
                new byte[33]);

        mockMvc.perform(multipart("/api/admin/uploads/product-images")
                .file(file)
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Product image must be 32 bytes or smaller"));
    }

    @Test
    void uploadProductImageRequiresAdminAuthenticationAndCsrf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "nuez.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[] { 1, 2, 3 });

        mockMvc.perform(multipart("/api/admin/uploads/product-images")
                .file(file)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/admin/uploads/product-images")
                .file(file)
                .with(httpBasic("storefront-user", "storefront-pass"))
                .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listIncludesGranelReservedBaseGrams() throws Exception {
        Category category = createCategory();
        Product product = new Product();
        product.setName("Almendra a granel");
        product.setSlug("almendra-granel");
        product.setDescription("desc");
        product.setActive(true);
        product.setCategory(category);
        product.setProductType(ProductType.GRANEL);
        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        product.setStockBaseGrams(5000);
        product.setStockReservedBaseGrams(750);
        productRepository.save(product);

        mockMvc.perform(get("/api/admin/products")
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stockBaseGrams").value(5000))
                .andExpect(jsonPath("$[0].stockReservedBaseGrams").value(750));
    }

    private Category createCategory() {
        Category category = new Category();
        category.setName("Frutos secos");
        category.setSlug("frutos-secos");
        category.setActive(true);
        return categoryRepository.save(category);
    }

    private ObjectNode validPayload(java.util.UUID categoryId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", "Almendra");
        payload.put("slug", "almendra");
        payload.put("description", "desc");
        payload.put("active", true);
        payload.put("categoryId", categoryId.toString());
        payload.put("productType", "ENVASADO");
        payload.put("inventoryPolicy", "PER_VARIANT");
        payload.putNull("stockBaseGrams");

        ArrayNode variants = payload.putArray("variants");
        ObjectNode variant = variants.addObject();
        variant.putNull("id");
        variant.put("sku", "SKU-1");
        variant.put("unitType", "WEIGHT");
        variant.put("weightGrams", 500);
        variant.put("unitLabel", "bolsa 500 g");
        variant.put("price", "6000.00");
        variant.put("stockAvailable", 5);
        variant.put("stockReserved", 0);
        variant.put("active", true);
        variant.putNull("attributesJson");
        return payload;
    }

    private ObjectNode image(String url, int sortOrder, boolean primary) {
        ObjectNode image = objectMapper.createObjectNode();
        image.putNull("id");
        image.put("url", url);
        image.put("altText", "alt");
        image.put("sortOrder", sortOrder);
        image.put("primary", primary);
        return image;
    }
}
